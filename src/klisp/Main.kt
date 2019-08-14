package klisp

fun main(args: Array<String>) {
    val env = initEnv()

    while(true) {
        print("> ")
        val input = readLine() ?: return
        //val tokens = KLispReader.tokenize(input).toList()

        if(input.isNotBlank()) {
            try {
                println(eval(input, env))
                println()
            } catch (ex: Exception) {
                System.err.println(ex.message)
                println()
            }
        }
    }
}

