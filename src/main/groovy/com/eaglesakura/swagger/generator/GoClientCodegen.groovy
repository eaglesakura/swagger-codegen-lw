package com.eaglesakura.swagger.generator

import io.swagger.codegen.*
import io.swagger.models.properties.ArrayProperty
import io.swagger.models.properties.MapProperty
import io.swagger.models.properties.Property
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GoClientCodegen extends DefaultCodegen implements CodegenConfig {
    static Logger LOGGER = LoggerFactory.getLogger(GoClientCodegen.class)

    protected String packageName = "swagger"
    protected String packageVersion = "1.0.0"

    CodegenType getTag() {
        return CodegenType.CLIENT
    }

    String getName() {
        return "go-client"
    }

    String getHelp() {
        return "Generates a Golang Client library."
    }

    GoClientCodegen() {
        super()
        outputFolder = "generated-code/go-client"
        modelTemplateFiles.put("model.mustache", ".go")
        apiTemplateFiles.put("api.mustache", ".go")

        embeddedTemplateDir = templateDir = "go-client"

        setReservedWordsLowerCase(
                Arrays.asList(
                        // data type
                        "string", "bool", "uint", "uint8", "uint16", "uint32", "uint64",
                        "int", "int8", "int16", "int32", "int64", "float32", "float64",
                        "complex64", "complex128", "rune", "byte", "uintptr",

                        "break", "default", "func", "interface", "select",
                        "case", "defer", "go", "map", "struct",
                        "chan", "else", "goto", "package", "switch",
                        "const", "fallthrough", "if", "range", "type",
                        "continue", "for", "import", "return", "var", "error", "ApiResponse")
                // Added "error" as it's used so frequently that it may as well be a keyword
        )

        defaultIncludes = new HashSet<String>(
                Arrays.asList(
                        "map",
                        "array")
        )

        languageSpecificPrimitives = new HashSet<String>(
                Arrays.asList(
                        "string",
                        "bool",
                        "uint",
                        "uint32",
                        "uint64",
                        "int",
                        "int32",
                        "int64",
                        "float32",
                        "float64",
                        "complex64",
                        "complex128",
                        "rune",
                        "byte")
        )

        instantiationTypes.clear()
        /*instantiationTypes.put("array", "GoArray")
        instantiationTypes.put("map", "GoMap")*/

        typeMapping.clear()
        typeMapping.put("integer", "int32")
        typeMapping.put("long", "int64")
        typeMapping.put("number", "float32")
        typeMapping.put("float", "float32")
        typeMapping.put("double", "float64")
        typeMapping.put("boolean", "bool")
        typeMapping.put("string", "string")
        typeMapping.put("UUID", "string")
        typeMapping.put("date", "time.Time")
        typeMapping.put("DateTime", "time.Time")
        typeMapping.put("password", "string")
        typeMapping.put("File", "[]byte")
        typeMapping.put("file", "[]byte")
        // map binary to string as a workaround
        // the correct solution is to use []byte
        typeMapping.put("binary", "string")
        typeMapping.put("ByteArray", "string")
        typeMapping.put("object", "interface{}")

        importMapping = new HashMap<String, String>()
        importMapping.put("time.Time", "time")
        importMapping.put("*os.File", "os")
        importMapping.put("os", "io/ioutil")

        cliOptions.clear()
        cliOptions.add(new CliOption(CodegenConstants.PACKAGE_NAME, "Go package name (convention: lowercase).")
                .defaultValue("swagger"))
        cliOptions.add(new CliOption(CodegenConstants.PACKAGE_VERSION, "Go package version.")
                .defaultValue("1.0.0"))
        cliOptions.add(new CliOption(CodegenConstants.HIDE_GENERATION_TIMESTAMP, "hides the timestamp when files were generated")
                .defaultValue(Boolean.TRUE.toString()))

    }

    @Override
    void processOpts() {
        super.processOpts()

        // default HIDE_GENERATION_TIMESTAMP to true
        if (!additionalProperties.containsKey(CodegenConstants.HIDE_GENERATION_TIMESTAMP)) {
            additionalProperties.put(CodegenConstants.HIDE_GENERATION_TIMESTAMP, Boolean.TRUE.toString())
        } else {
            additionalProperties.put(CodegenConstants.HIDE_GENERATION_TIMESTAMP,
                    Boolean.valueOf(additionalProperties().get(CodegenConstants.HIDE_GENERATION_TIMESTAMP).toString()))
        }

        if (additionalProperties.containsKey(CodegenConstants.PACKAGE_NAME)) {
            setPackageName((String) additionalProperties.get(CodegenConstants.PACKAGE_NAME))
        } else {
            setPackageName("swagger")
        }

        if (additionalProperties.containsKey(CodegenConstants.PACKAGE_VERSION)) {
            setPackageVersion((String) additionalProperties.get(CodegenConstants.PACKAGE_VERSION))
        } else {
            setPackageVersion("1.0.0")
        }

        additionalProperties.put(CodegenConstants.PACKAGE_NAME, packageName)
        additionalProperties.put(CodegenConstants.PACKAGE_VERSION, packageVersion)
        modelPackage = packageName
        apiPackage = packageName

//        additionalProperties.put("apiDocPath", apiDocPath)
//        additionalProperties.put("modelDocPath", modelDocPath)
//        supportingFiles.add(new SupportingFile("README.mustache", "", "README.md"))
    }

    @Override
    String escapeReservedWord(String name) {
        // Can't start with an underscore, as our fields need to start with an
        // UppercaseLetter so that Go treats them as /visible.

        // Options?
        // - MyName
        // - AName
        // - TheName
        // - XName
        // - X_Name
        // ... or maybe a suffix?
        // - Name_ ... think this will work. 
        if (this.reservedWordsMappings().containsKey(name)) {
            return this.reservedWordsMappings().get(name)
        }
        return camelize(name) + '_'
    }

    @Override
    String apiFileFolder() {
        return outputFolder + File.separator
    }

    String modelFileFolder() {
        return outputFolder + File.separator
    }

    @Override
    String toVarName(String name) {
        // replace - with _ e.g. created-at => created_at
        name = sanitizeName(name.replaceAll("-", "_"))

        // if it's all uppper case, do nothing
        if (name.matches('^[A-Z_]*$'))
            return name

        // camelize (lower first character) the variable name
        // pet_id => PetId
        name = camelize(name)

        // for reserved word or word starting with number, append _
        if (isReservedWord(name))
            name = escapeReservedWord(name)

        // for reserved word or word starting with number, append _
        if (name.matches('^\\d.*'))
            name = "Var" + name

        return name
    }

    @Override
    String toParamName(String name) {
        // params should be lowerCamelCase. E.g. "person Person", instead of
        // "Person Person".
        //
        // REVISIT: Actually, for idiomatic go, the param name should
        // really should just be a letter, e.g. "p Person"), but we'll get
        // around to that some other time... Maybe.
        return camelize(toVarName(name), false)
    }

    @Override
    String toModelName(String name) {
        if (!StringUtils.isEmpty(modelNamePrefix)) {
            name = modelNamePrefix + "_" + name
        }

        if (!StringUtils.isEmpty(modelNameSuffix)) {
            name = name + "_" + modelNameSuffix
        }

        name = sanitizeName(name)

        // model name cannot use reserved keyword, e.g. return
        if (isReservedWord(name)) {
            LOGGER.warn(name + " (reserved word) cannot be used as model name. Renamed to " + ("model_" + name))
            name = "model_" + name // e.g. return => ModelReturn (after camelize)
        }

        // model name starts with number
        if (name.matches("^\\d.*")) {
            LOGGER.warn(name + " (model name starts with number) cannot be used as model name. Renamed to " + ("model_" + name))
            name = "model_" + name // e.g. 200Response => Model200Response (after camelize)
        }

        return camelize(underscore(name))
    }

    @Override
    String toModelFilename(String name) {
        if (!StringUtils.isEmpty(modelNamePrefix)) {
            name = modelNamePrefix + "_" + name
        }

        if (!StringUtils.isEmpty(modelNameSuffix)) {
            name = name + "_" + modelNameSuffix
        }

        name = sanitizeName(name)

        // model name cannot use reserved keyword, e.g. return
        if (isReservedWord(name)) {
            LOGGER.warn(name + " (reserved word) cannot be used as model name. Renamed to " + ("model_" + name))
            name = "model_" + name // e.g. return => ModelReturn (after camelize)
        }

        // model name starts with number
        if (name.matches("^\\d.*")) {
            LOGGER.warn(name + " (model name starts with number) cannot be used as model name. Renamed to " + ("model_" + name))
            name = "model_" + name // e.g. 200Response => Model200Response (after camelize)
        }

        def result = underscore(name)
        if (result.endsWith("_model")) {
            result = result.substring(0, result.length() - "_model".length())
        }

        if (!result.startsWith("model_")) {
            result = "model_" + result
        }
        return result
    }

    @Override
    String toApiFilename(String name) {
        // replace - with _ e.g. created-at => created_at
        name = name.replaceAll("-", "_")
        // FIXME: a parameter should not be assigned. Also declare the methods parameters as 'final'.

        // e.g. PetApi.go => pet_api.go
        return "api_" + underscore(name)
    }

    /**
     * Overrides postProcessParameter to add a vendor extension "x-exportParamName".
     * This is useful when paramName starts with a lowercase letter, but we need that
     * param to be exportable (starts with an Uppercase letter).
     *
     * @param parameter CodegenParameter object to be processed.
     */
    @Override
    void postProcessParameter(CodegenParameter parameter) {

        // Give the base class a chance to process
        super.postProcessParameter(parameter)

        char firstChar = parameter.paramName.charAt(0)

        if (Character.isUpperCase(firstChar)) {
            // First char is already uppercase, just use paramName.
            parameter.vendorExtensions.put("x-exportParamName", parameter.paramName)

        }

        // It's a lowercase first char, let's convert it to uppercase
        StringBuilder sb = new StringBuilder(parameter.paramName)
        sb.setCharAt(0, Character.toUpperCase(firstChar))
        parameter.vendorExtensions.put("x-exportParamName", sb.toString())
    }


    @Override
    String getTypeDeclaration(Property p) {
        if (p instanceof ArrayProperty) {
            ArrayProperty ap = (ArrayProperty) p
            Property inner = ap.getItems()
            def type = getTypeDeclaration(inner)

            if (type.charAt(0) >= 'a' && type.charAt(0) <= 'z') {
                return "[]" + getTypeDeclaration(inner)
            } else {
                return getTypeDeclaration(inner) + "Array"
            }
        } else if (p instanceof MapProperty) {
            MapProperty mp = (MapProperty) p
            Property inner = mp.getAdditionalProperties()

            return getSwaggerType(p) + "[string]" + getTypeDeclaration(inner)
        }
        //return super.getTypeDeclaration(p)

        // Not using the supertype invocation, because we want to UpperCamelize
        // the type.
        String swaggerType = getSwaggerType(p)
        if (typeMapping.containsKey(swaggerType)) {
            return typeMapping.get(swaggerType)
        }

        if (typeMapping.containsValue(swaggerType)) {
            return swaggerType
        }

        if (languageSpecificPrimitives.contains(swaggerType)) {
            return swaggerType
        }

        return toModelName(swaggerType)
    }

    @Override
    String getSwaggerType(Property p) {
        String swaggerType = super.getSwaggerType(p)
        String type = null
        if (typeMapping.containsKey(swaggerType)) {
            type = typeMapping.get(swaggerType)
            if (languageSpecificPrimitives.contains(type))
                return (type)
        } else
            type = swaggerType
        return type
    }

    @Override
    String toOperationId(String operationId) {
        String sanitizedOperationId = sanitizeName(operationId)

        // method name cannot use reserved keyword, e.g. return
        if (isReservedWord(sanitizedOperationId)) {
            LOGGER.warn(operationId + " (reserved word) cannot be used as method name. Renamed to " + camelize("call_" + operationId))
            sanitizedOperationId = "call_" + sanitizedOperationId
        }

        return camelize(sanitizedOperationId)
    }

    @Override
    Map<String, Object> postProcessOperations(Map<String, Object> objs) {
        @SuppressWarnings("unchecked")
        Map<String, Object> objectMap = (Map<String, Object>) objs.get("operations")
        @SuppressWarnings("unchecked")
        List<CodegenOperation> operations = (List<CodegenOperation>) objectMap.get("operation")
        for (CodegenOperation operation : operations) {
            // http method verb conversion (e.g. PUT => Put)
            operation.httpMethod = camelize(operation.httpMethod.toLowerCase())
        }

        // remove model imports to avoid error
        List<Map<String, String>> imports = (List<Map<String, String>>) objs.get("imports")
        if (imports == null)
            return objs

        Iterator<Map<String, String>> iterator = imports.iterator()
        while (iterator.hasNext()) {
            String _import = iterator.next().get("import")
            if (_import.startsWith(apiPackage()))
                iterator.remove()
        }
        // if the return type is not primitive, import encoding/json
        for (CodegenOperation operation : operations) {
            if (operation.returnBaseType != null && needToImport(operation.returnBaseType)) {
                imports.add(createMapping("import", "encoding/json"))
                break //just need to import once
            }
        }

        // this will only import "fmt" if there are items in pathParams
        for (CodegenOperation operation : operations) {
            if (operation.pathParams != null && operation.pathParams.size() > 0) {
                imports.add(createMapping("import", "fmt"))
                break //just need to import once
            }
        }

        // recursively add import for mapping one type to multiple imports
        List<Map<String, String>> recursiveImports = (List<Map<String, String>>) objs.get("imports")
        if (recursiveImports == null)
            return objs

        ListIterator<Map<String, String>> listIterator = imports.listIterator()
        while (listIterator.hasNext()) {
            String _import = listIterator.next().get("import")
            // if the import package happens to be found in the importMapping (key)
            // add the corresponding import package to the list
            if (importMapping.containsKey(_import)) {
                listIterator.add(createMapping("import", importMapping.get(_import)))
            }
        }

        return objs
    }

    @Override
    Map<String, Object> postProcessModels(Map<String, Object> objs) {
        // remove model imports to avoid error
        List<Map<String, String>> imports = (List<Map<String, String>>) objs.get("imports")
        final String prefix = modelPackage()
        Iterator<Map<String, String>> iterator = imports.iterator()
        while (iterator.hasNext()) {
            String _import = iterator.next().get("import")
            if (_import.startsWith(prefix))
                iterator.remove()
        }

        // recursively add import for mapping one type to multiple imports
        List<Map<String, String>> recursiveImports = (List<Map<String, String>>) objs.get("imports")
        if (recursiveImports == null)
            return objs

        ListIterator<Map<String, String>> listIterator = imports.listIterator()
        while (listIterator.hasNext()) {
            String _import = listIterator.next().get("import")
            // if the import package happens to be found in the importMapping (key)
            // add the corresponding import package to the list
            if (importMapping.containsKey(_import)) {
                listIterator.add(createMapping("import", importMapping.get(_import)))
            }
        }

        return objs
    }

    @Override
    protected boolean needToImport(String type) {
        return !defaultIncludes.contains(type) && !languageSpecificPrimitives.contains(type)
    }

    void setPackageName(String packageName) {
        this.packageName = packageName
    }

    void setPackageVersion(String packageVersion) {
        this.packageVersion = packageVersion
    }

    @Override
    String escapeQuotationMark(String input) {
        // remove " to avoid code injection
        return input.replace("\"", "")
    }

    @Override
    String escapeUnsafeCharacters(String input) {
        return input.replace("*/", "*_/").replace("/*", "/_*")
    }

    Map<String, String> createMapping(String key, String value) {
        Map<String, String> customImport = new HashMap<String, String>()
        customImport.put(key, value)

        return customImport
    }
}
