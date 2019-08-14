package klisp

class KLispEnv(private val symbolTable: MutableMap<KLispSymbol, KLispSexp>) {
    constructor() : this(mutableMapOf<KLispSymbol, KLispSexp>())

    constructor(env: KLispEnv) : this(env.symbolTable)

    constructor(env: KLispEnv, local_vars: List<KLispSymbol>, local_values: KLispList) : this(env) {
        for(i in 0 until local_vars.size) {
            val symbol = local_vars[i]
            if(symbol.toString() == "&rest") {
                if(i == local_vars.size - 1) {
                    throw KLispException("Bind end with &rest")
                }
                val nextSymbol = local_vars[i+1]
                add(nextSymbol, local_values.subList(i))
                break
            }

            add(local_vars[i], local_values[i])
        }
    }

    fun apply(symbol: KLispSymbol): KLispSexp {
        return symbolTable[symbol] ?: throw KLispException("Symbol '$symbol' not found")
    }

    fun apply(list: KLispList): KLispSexp {
        val head = list.car()

        return if(head is KLispSymbol) {
            when(head.toString()) {
                "lambda" -> parseLambda(list.cdr())
                "define" -> parseDefine(list.cdr())
                "let" -> parseLet(list.cdr())
                "if" -> parseIf(list.cdr())
                "quote" -> parseQuote(list.cdr())
                else -> applyFunction(list)
            }
        } else {
            applyFunction(list)
        }
    }

    private fun applyFunction(list: KLispList): KLispSexp {
        val func = list.car().eval(this) as? KLispLambda ?: throw KLispException("Not a function object")
        return func(KLispList.createList(list.cdr().map { it.eval(this) }))
    }

    private fun parseLambda(args: KLispList): KLispLambda {
        val argList = (args[0] as? KLispList ?: throw KLispException("Not a parameter list"))
        val varList = argList.filterIsInstance<KLispSymbol>()

        if(argList.size != varList.size) {
            throw KLispException("Invalid parameter list")
        }

        val body = args[1]

        return object : KLispLambda() {
            override fun invoke(args: KLispList): KLispSexp {
                return body.eval(KLispEnv(this@KLispEnv, varList, args))
            }
        }
    }

    private fun parseDefine(args: KLispList): KLispSymbol {
        if(args.size != 2) {
            throw KLispException("Invalid number of arguments", "define")
        }

        val symbol = args[0] as? KLispSymbol ?: throw KLispException("Not a appearance", "define")
        val value = args[1].eval(this)

        this.add(symbol, value)

        return symbol
    }

    private fun parseLet(args: KLispList): KLispSexp {
        if(args.size < 1) {
            throw KLispException("Too few arguments")
        }

        val binds = (args[0] as? KLispList ?: throw KLispException("Not a parameter list"))
        val localEnv = KLispEnv(this)

        /* Create local scope */
        for(bind in binds) {
            when(bind) {
                is KLispSymbol -> {
                    localEnv.add(bind, KLispList.NIL)
                }

                is KLispList -> {
                    when(bind.size) {
                        0 -> throw KLispException("Nil cannot be bound")
                        1 -> localEnv.add((bind[0] as? KLispSymbol
                                ?: throw KLispException("Invalid appearance", bind[0].toString())), KLispList.NIL)
                        2 -> {
                            val symbol = bind[0] as? KLispSymbol
                                    ?: throw KLispException("Invalid appearance", bind[0].toString())
                            val eq = bind[1]

                            localEnv.add(symbol, eq.eval(this))
                        }
                        else -> throw KLispException("Invalid let binding")
                    }
                }
            }
        }

        return when(args.size) {
            1 -> KLispList.NIL
            else -> {
                var result: KLispSexp = KLispList.NIL
                for(clause in args.subList(1)) {
                    result = clause.eval(localEnv)
                }
                result
            }
        }
    }

    private fun parseIf(list: KLispList): KLispSexp {
        return when(list.size) {
            2 -> {
                if(list[0].eval(this) != KLispList.NIL) {
                    list[1].eval(this)
                } else {
                    KLispList.NIL
                }
            }

            3 -> {
                if(list[0].eval(this) != KLispList.NIL) {
                    list[1].eval(this)
                } else {
                    list[2].eval(this)
                }
            }

            else -> throw KLispException("Invalid if clause")
        }
    }

    private fun parseQuote(args: KLispList): KLispSexp {
        if(args.size != 1) {
            throw KLispException("Too few arguments", "quote")
        }

        return args[0]
    }

    fun add(symbol: KLispSymbol, value: KLispSexp) {
        symbolTable += symbol to value
    }
}