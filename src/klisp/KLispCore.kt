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
            if(args.size() != 1) {
                throw KLispException("Invalid argument", "eval")
            }

            return args[0].eval(env)
        }
    })

    env.add(KLispSymbol("apply"), object : KLispLambda() {
        override fun invoke(args: KLispList): KLispSexp {
            if(args.size() != 2) {
                throw KLispException("Invalid argument", "apply")
            }

            val func = args[0] as? KLispLambda ?: throw KLispException("Invalid function", "apply")
            val argList = args[1] as? KLispList ?: throw KLispException("Invalid argument list", "apply")

            return env.applyFunction(func, argList)
        }
    })

    env.add(KLispSymbol("atom?"), object : KLispLambda() {
        override fun invoke(args: KLispList): KLispSexp {
            if(args.size() != 1) {
                throw KLispException("Invalid argument", "atom?")
            }

            return if(args[0] is KLispAtom) KLispSymbol.T else KLispList.NIL
        }
    })

    env.add(KLispSymbol("a"), KLispDouble(1.0))
    env.add(KLispSymbol("+"), object : KLispLambda() {
        override fun invoke(args: KLispList): KLispSexp {
            return args.fold(KLispFraction(0)) { acc: KLispNumber, elem: KLispSexp ->
                acc + (elem as? KLispNumber ?: throw KLispException("Not a Number", "+"))
            }
        }
    })

    env.add(KLispSymbol("-"), object : KLispLambda() {
        override fun invoke(args: KLispList): KLispSexp {
            return when(args.size()) {
                0 -> throw KLispException("No argument", "-")
                1 -> KLispFraction(-1) * (args[0] as? KLispNumber ?: throw KLispException("Not a Number", "-"))
                else -> {
                    val first = args[0] as? KLispNumber ?: throw KLispException("Not a Number", "-")
                    args.subList(1).fold(first) { acc: KLispNumber, elem: KLispSexp ->
                        acc - (elem as? KLispNumber ?: throw KLispException("Not a Number", "-"))
                    }
                }
            }
        }
    })

    env.add(KLispSymbol("*"), object : KLispLambda() {
        override fun invoke(args: KLispList): KLispSexp {
            return args.fold(KLispFraction(1)) { acc: KLispNumber, elem: KLispSexp ->
                acc * (elem as? KLispNumber ?: throw KLispException("Not a Number", "*"))
            }
        }
    })

    env.add(KLispSymbol("/"), object : KLispLambda() {
        override fun invoke(args: KLispList): KLispSexp {
            return when(args.size()) {
                0 -> throw KLispException("No argument", "/")
                1 -> KLispFraction(1) / (args[0] as? KLispNumber ?: throw KLispException("Not a Number", "/"))
                else -> {
                    val first = args[0] as? KLispNumber ?: throw KLispException("Not a Number", "/")
                    args.subList(1).fold(first) { acc: KLispNumber, elem: KLispSexp ->
                        acc / (elem as? KLispNumber ?: throw KLispException("Not a Number", "/"))
                    }
                }
            }
        }
    })

    env.add(KLispSymbol("="), object : KLispLambda() {
        override fun invoke(args: KLispList): KLispSexp {
            if(args.size() == 0) {
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

    env.add(KLispSymbol("cons"), object : KLispLambda() {
        override fun invoke(args: KLispList): KLispSexp {
            if(args.size() != 2) {
                throw KLispException("Invalid argument", "cons")
            }

            return KLispCons(args[0], args[1])
        }
    })

    env.add(KLispSymbol("list"), object : KLispLambda() {
        override fun invoke(args: KLispList): KLispSexp {
            return args
        }
    })

    env.add(KLispSymbol("append"), object : KLispLambda() {
        override fun invoke(args: KLispList): KLispSexp {
            val concat = mutableListOf<KLispSexp>()

            args.forEach { list ->
                (list as? KLispList ?: throw KLispException("Not a list", "append")).forEach {
                    concat.add(it)
                }
            }

            return KLispCons.createList(concat)
        }
    })

    env.add(KLispSymbol("car"), object : KLispLambda() {
        override fun invoke(args: KLispList): KLispSexp {
            if(args.size() != 1) {
                throw KLispException("Invalid argument", "car")
            }

            val list = args[0] as? KLispList ?: throw KLispException("Argument must be list", "car")
            return list.car()
        }
    })

    env.add(KLispSymbol("cdr"), object : KLispLambda() {
        override fun invoke(args: KLispList): KLispSexp {
            if(args.size() != 1) {
                throw KLispException("Invalid argument", "cdr")
            }

            val list = args[0] as? KLispList ?: throw KLispException("Argument must be list", "cdr")
            return list.cdr()
        }
    })

    env.add(KLispSymbol("len"), object : KLispLambda() {
        override fun invoke(args: KLispList): KLispSexp {
            if(args.size() != 1) {
                throw KLispException("Invalid argument", "len")
            }

            return KLispFraction((args[0] as? KLispList ?: throw KLispException("Not a list", "len")).size().toLong())
        }
    })



    env.add(KLispSymbol("quit"), object : KLispLambda() {
        override fun invoke(args: KLispList): KLispSexp {
            exitProcess(0)
        }
    })

    return env
}