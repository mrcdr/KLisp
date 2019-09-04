package klisp

class KLispEnv(private val symbolTable: MutableMap<KLispSymbol, KLispSexp>) {
    constructor() : this(mutableMapOf<KLispSymbol, KLispSexp>())

    constructor(env: KLispEnv) : this(env.symbolTable.toMutableMap()) // Copy the map

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
        val args = list.cdr() as? KLispList ?: throw KLispException("arguments must be list", head.toString())

        return if(head is KLispSymbol) {
            when(head.toString()) {
                "lambda" -> parseLambda(args)
                "define" -> parseDefine(args)
                "let" -> parseLet(args)
                "if" -> parseIf(args)
                KLispReader.QUOTE -> parseQuote(args)
                KLispReader.QUASIQUOTE -> parseQuasiQuote(args)
                KLispReader.UNQUOTE, KLispReader.UNQUOTE_SPLICE -> throw KLispException("Illegal unquote")
                else -> {
                    applyFunction(head, args)
                }
            }
        } else {
            applyFunction(head, args)
        }
    }

    fun applyFunction(head: KLispSexp, args: KLispList): KLispSexp {
        val func = head.eval(this) as? KLispLambda ?: throw KLispException("Not a function object", "$head")
        return func(KLispCons.createList(args.map { it.eval(this) }))
    }

    private fun parseLambda(args: KLispList): KLispLambda {
        val argList = (args.car() as? KLispList ?: throw KLispException("Not a parameter list"))
        val varList = argList.filterIsInstance<KLispSymbol>()

        if(argList.size() != varList.size) {
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
        if(args.size() != 2) {
            throw KLispException("Invalid number of arguments", "define")
        }

        val symbol = args.car() as? KLispSymbol ?: throw KLispException("Not a appearance", "define")
        if(this.hasSymbol(symbol)) {
            throw KLispException("Symbol '$symbol' already exists", "define")
        }

        val value = args[1].eval(this)

        this.add(symbol, value)

        return symbol
    }

    private fun parseLet(args: KLispList): KLispSexp {
        if(args.size() < 1) {
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
                    when(bind.size()) {
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

        return when(args.size()) {
            1 -> KLispList.NIL
            else -> {
                var result: KLispSexp = KLispList.NIL
                for(clause in (args.cdr() as KLispList)) { // size is greater than 1
                    result = clause.eval(localEnv)
                }
                result
            }
        }
    }

    private fun parseIf(list: KLispList): KLispSexp {
        return when(list.size()) {
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
        if(args.size() != 1) {
            throw KLispException("Too few arguments", "quote")
        }

        return args[0]
    }

    private fun parseQuasiQuote(args: KLispList): KLispSexp {
        if(args.size() != 1) {
            throw KLispException("Invalid quasi-quote")
        }

        val expanded = expandQuasiQuote(args[0], 0)
        return expanded.eval(this)
    }

    private fun expandQuasiQuote(form: KLispSexp, depth: Int): KLispSexp {
        val quasiquote = KLispReader.QUASIQUOTE
        val unquote = KLispReader.UNQUOTE
        val unquoteSplice = KLispReader.UNQUOTE_SPLICE

        return if(form is KLispList && form != KLispList.NIL) {
            val head = form.car()
            if(head is KLispSymbol) {
                when (head.toString()) {
                    quasiquote -> KLispCons.createList(
                            KLispSymbol("cons"),
                            KLispSymbol(quasiquote).quote(),
                            expandQuasiQuote(form.cdr(), depth+1)
                    )

                    unquote -> {
                        when {
                            depth > 0 -> KLispCons.createList(
                                    KLispSymbol("cons"),
                                    form.car().quote(),
                                    expandQuasiQuote(form.cdr(), depth-1)
                            )

                            else -> (form.cdr() as? KLispList ?: throw KLispException("Invalid unquote")).car()
                        }
                    }

                    unquoteSplice -> {
                        when {
                            depth > 0 -> KLispCons.createList(
                                    KLispSymbol("cons"),
                                    form.car().quote(),
                                    expandQuasiQuote(form.cdr(), depth-1)
                            )
                            else -> throw KLispException("Invalid unquote-splicing")
                        }
                    }
                    else -> KLispCons.createList(
                            KLispSymbol("append"),
                            expandQuasiQuoteList(form.car(), depth),
                            expandQuasiQuote(form.cdr(), depth)
                    )
                }
            } else {
                KLispCons.createList(
                        KLispSymbol("append"),
                        expandQuasiQuoteList(form.car(), depth),
                        expandQuasiQuote(form.cdr(), depth)
                )
            }
        } else {
            form.quote()
        }
    }

    private fun expandQuasiQuoteList(form: KLispSexp, depth: Int): KLispList {
        val quasiquote = KLispReader.QUASIQUOTE
        val unquote = KLispReader.UNQUOTE
        val unquoteSplice = KLispReader.UNQUOTE_SPLICE

        return if(form is KLispList && form != KLispList.NIL) {
            val head = form.car()
            if(head is KLispSymbol) {
                when (head.toString()) {
                    quasiquote -> KLispCons.createList(
                            KLispSymbol("list"),
                            KLispCons.createList(
                                    KLispSymbol("cons"),
                                    KLispSymbol(quasiquote).quote(),
                                    expandQuasiQuote(form.cdr(), depth+1)
                            )
                    )

                    unquote -> {
                        when {
                            depth > 0 -> KLispCons.createList(
                                    KLispSymbol("list"),
                                    KLispCons.createList(
                                            KLispSymbol("cons"),
                                            form.car().quote(),
                                            expandQuasiQuote(form.cdr(), depth-1)
                                    )
                            )

                            else -> KLispCons(KLispSymbol("list"), form.cdr())
                        }
                    }

                    unquoteSplice -> {
                        when {
                            depth > 0 -> KLispCons.createList(
                                    KLispSymbol("list"),
                                    KLispCons.createList(
                                            KLispSymbol("cons"),
                                            form.car().quote(),
                                            expandQuasiQuote(form.cdr(), depth-1)
                                    )
                            )

                            else -> KLispCons(KLispSymbol("append"),
                                    form.cdr() as? KLispList ?: throw KLispException("Invalid unquote-splicing"))
                        }
                    }

                    else -> KLispCons.createList(
                            KLispSymbol("list"),
                            KLispCons.createList(
                                    KLispSymbol("append"),
                                    expandQuasiQuoteList(form.car(), depth),
                                    expandQuasiQuote(form.cdr(), depth)
                            ))
                }
            } else {
                KLispCons.createList(
                        KLispSymbol("list"),
                        KLispCons.createList(
                                KLispSymbol("append"),
                                expandQuasiQuoteList(form.car(), depth),
                                expandQuasiQuote(form.cdr(), depth)
                        ))
            }
        } else {
            KLispCons.createList(form).quote()
        }
    }

    fun add(symbol: KLispSymbol, value: KLispSexp) {
        symbolTable += symbol to value
    }

    fun hasSymbol(symbol: KLispSymbol) = this.symbolTable.containsKey(symbol)
}