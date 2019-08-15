package klisp

import kotlin.system.exitProcess

fun eval(input: String, env: KLispEnv): KLispSexp {
    val reader = KLispReader(input)
    val form = readForm(reader)
    return form.eval(env)
}

fun initEnv(): KLispEnv {
    val env = KLispEnv()

    // In lisp, eval runs in null lexical environment
    env.add(KLispSymbol("eval"), object : KLispLambda() {
        override fun invoke(args: KLispList): KLispSexp {
            if(args.size != 1) {
                throw KLispException("Invalid argument", "eval")
            }

            return args[0].eval(env)
        }
    })

    env.add(KLispSymbol("a"), KLispDouble(1.0))
    env.add(KLispSymbol("+"), object : KLispLambda() {
        override fun invoke(args: KLispList): KLispSexp {
            val x = args[0] as? KLispNumber ?: throw KLispException("Not a Number", "+")
            val y = args[1] as? KLispNumber ?: throw KLispException("Not a Number", "+")

            return x + y
        }
    })

    env.add(KLispSymbol("-"), object : KLispLambda() {
        override fun invoke(args: KLispList): KLispSexp {
            val x = args[0] as? KLispNumber ?: throw KLispException("Not a Number", "-")
            val y = args[1] as? KLispNumber ?: throw KLispException("Not a Number", "-")

            return x - y
        }
    })

    env.add(KLispSymbol("*"), object : KLispLambda() {
        override fun invoke(args: KLispList): KLispSexp {
            val x = args[0] as? KLispNumber ?: throw KLispException("Not a Number", "*")
            val y = args[1] as? KLispNumber ?: throw KLispException("Not a Number", "*")

            return x * y
        }
    })

    env.add(KLispSymbol("/"), object : KLispLambda() {
        override fun invoke(args: KLispList): KLispSexp {
            val x = args[0] as? KLispNumber ?: throw KLispException("Not a Number", "/")
            val y = args[1] as? KLispNumber ?: throw KLispException("Not a Number", "/")

            return x / y
        }
    })

    env.add(KLispSymbol("="), object : KLispLambda() {
        override fun invoke(args: KLispList): KLispSexp {
            if(args.size == 0) {
                throw KLispException("No argument", "=")
            }
            val num = args[0] as? KLispNumber ?: throw KLispException("Not a number", "=")

            return args.all {
                (it as? KLispNumber ?: throw KLispException("Not a number", "=")) == num
            }.let {
                if(it) {
                    KLispSymbol.T
                } else {
                    KLispList.NIL
                }
            }
        }
    })

    env.add(KLispSymbol("list"), object : KLispLambda() {
        override fun invoke(args: KLispList): KLispSexp {
            return args
        }
    })

    env.add(KLispSymbol("car"), object : KLispLambda() {
        override fun invoke(args: KLispList): KLispSexp {
            if(args.size != 1) {
                throw KLispException("Invalid argument", "car")
            }

            val list = args[0] as? KLispList ?: throw KLispException("Argument must be list", "car")
            return list.car()
        }
    })

    env.add(KLispSymbol("cdr"), object : KLispLambda() {
        override fun invoke(args: KLispList): KLispSexp {
            if(args.size != 1) {
                throw KLispException("Invalid argument", "cdr")
            }

            val list = args[0] as? KLispList ?: throw KLispException("Argument must be list", "cdr")
            return list.cdr()
        }
    })

    env.add(KLispSymbol("quit"), object : KLispLambda() {
        override fun invoke(args: KLispList): KLispSexp {
            exitProcess(0)
        }
    })

    return env
}