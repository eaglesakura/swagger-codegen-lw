package com.eaglesakura.swagger.generator

import com.eaglesakura.util.IOUtil
import io.swagger.codegen.SwaggerCodegen

class Generator {

    static void main(String[] args) {
        println("Workspace :: ${new File(".").absolutePath}")
//        IOUtil.cleanDirectory(new File("output"))
        SwaggerCodegen.main(args)
    }
}
