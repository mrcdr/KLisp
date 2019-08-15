package klisp

import kotlin.math.absoluteValue

abstract class KLispSexp {
    abstract fun eval(env: KLispEnv): KLispSexp
}

abstract class KLispAtom(protected val appearance: String) : KLispSexp() {
    override fun toString() = appearance
}

open class KLispSymbol(appearance: String) : KLispAtom(appearance) {
    companion object {
        val T = object : KLispSymbol("t") {
            override fun eval(env: KLispEnv): KLispSexp {
                return this
            }
        }
    }

    override fun eval(env: KLispEnv): KLispSexp {
        return env.apply(this)
    }

    override fun toString(): String = appearance

    override fun equals(other: Any?): Boolean {
        return other is KLispSymbol && (this.appearance == other.appearance)
    }

    override fun hashCode(): Int {
        return this.appearance.hashCode()
    }
}

abstract class KLispValue(appearance: String) : KLispAtom(appearance) {
    override fun eval(env: KLispEnv) = this
}

sealed class KLispNumber(appearance: String) : KLispValue(appearance) {
    abstract operator fun plus(other: KLispNumber): KLispNumber
    abstract operator fun minus(other: KLispNumber): KLispNumber
    abstract operator fun times(other: KLispNumber): KLispNumber
    abstract operator fun div(other: KLispNumber): KLispNumber
}

class KLispFraction(appearance: String) : KLispNumber(appearance) {
    val numerator: Long
    val denominator: Long

    init {
        val split = appearance.split("/")
        val numeratorTmp = split[0].toLong()
        val denominatorTmp = if(split.size == 2) split[1].toLong() else 1L

        val gcd = KLispUtil.gcd(numeratorTmp.absoluteValue, denominatorTmp.absoluteValue)
        val sign = if(numeratorTmp * denominatorTmp >= 0) 1L else -1L

        numerator = sign * numeratorTmp.absoluteValue / gcd
        denominator = denominatorTmp.absoluteValue / gcd
    }

    constructor(numerator: Long, denominator: Long)
            : this("$numerator/$denominator") // duplicated conversion

    override fun toString(): String {
        return if(this.isInteger()) {
            "$numerator"
        } else {
            "$numerator/$denominator"
        }
    }

    override fun equals(other: Any?) = ((other is KLispFraction) &&
            (this.numerator == other.numerator) &&
            (this.denominator == other.denominator))

    override fun hashCode(): Int {
        return this.numerator.hashCode() + this.denominator.hashCode()
    }

    override operator fun plus(other: KLispNumber): KLispNumber {
        return when(other) {
            is KLispFraction -> KLispFraction(this.numerator * other.denominator + other.numerator * this.denominator,
                    this.denominator * other.denominator)
            is KLispDouble -> KLispDouble(this.numerator.toDouble()/this.denominator.toDouble() + other.value)
        }
    }

    override operator fun minus(other: KLispNumber): KLispNumber {
        return when(other) {
            is KLispFraction -> KLispFraction(this.numerator * other.denominator - other.numerator * this.denominator,
                    this.denominator * other.denominator)
            is KLispDouble -> KLispDouble(this.numerator.toDouble()/this.denominator.toDouble() - other.value)
        }
    }

    override operator fun times(other: KLispNumber): KLispNumber {
        return when(other) {
            is KLispFraction -> KLispFraction(this.numerator * other.numerator, this.denominator * other.denominator)
            is KLispDouble -> KLispDouble(this.numerator.toDouble()/this.denominator.toDouble() * other.value)
        }
    }

    override operator fun div(other: KLispNumber): KLispNumber {
        return when(other) {
            is KLispFraction -> KLispFraction(this.numerator * other.denominator, this.denominator * other.numerator)
            is KLispDouble -> KLispDouble(this.numerator.toDouble()/this.denominator.toDouble() / other.value)
        }
    }

    fun isInteger() = (denominator == 1L)
}


class KLispDouble(val value: Double) : KLispNumber(value.toString()) {
    override fun toString(): String {
        return value.toString()
    }

    override fun equals(other: Any?) = (other is KLispDouble) && (this.value == other.value)

    override fun hashCode() = value.hashCode()

    override operator fun plus(other: KLispNumber): KLispNumber {
        return when(other) {
            is KLispFraction -> other + this
            is KLispDouble -> KLispDouble(this.value + other.value)
        }
    }

    override operator fun minus(other: KLispNumber): KLispNumber {
        return when(other) {
            is KLispFraction -> KLispDouble(this.value - other.numerator.toDouble()/other.denominator.toDouble())
            is KLispDouble -> KLispDouble(this.value - other.value)
        }
    }

    override operator fun times(other: KLispNumber): KLispNumber {
        return when(other) {
            is KLispFraction -> other * this
            is KLispDouble -> KLispDouble(this.value * other.value)
        }
    }

    override operator fun div(other: KLispNumber): KLispNumber {
        return when(other) {
            is KLispFraction -> KLispDouble(this.value * other.denominator / other.numerator)
            is KLispDouble -> KLispDouble(this.value / other.value)
        }
    }
}

class KLispString(private val str: String) : KLispValue(str) {
    override fun eval(env: KLispEnv): KLispString = this
    override fun toString(): String {
        return "\"$str\""
    }
}

open class KLispList private constructor(private val list: List<KLispSexp>) : KLispSexp(), Iterable<KLispSexp> {
    companion object {
        val NIL = object : KLispList(emptyList()) {
            override fun eval(env: KLispEnv) = this
            override fun car() = this
            override fun cdr() = this

            override fun toString() = "nil"
        }

        fun createList(list: List<KLispSexp>): KLispList {
            return if(list.isEmpty()) {
                NIL
            } else {
                KLispList(list)
            }
        }
    }

    override fun eval(env: KLispEnv): KLispSexp {
        return env.apply(this)
    }

    fun subList(start: Int) = createList(list.subList(start, list.size))

    open fun car() = list[0]
    open fun cdr() = subList(1)

    operator fun get(at: Int) = list[at]

    val size: Int
    get() {
        return list.size
    }

    override fun iterator() = list.iterator()

    override fun toString(): String {
        val builder = StringBuilder()
        builder.append("(")
        for(elem in list) {
            builder.append(elem)
            builder.append(" ")
        }
        builder.setLength(builder.length-1) // remove last space
        builder.append(")")

        return builder.toString()
    }
}

abstract class KLispLambda : KLispValue("lambda") {
    abstract operator fun invoke(args: KLispList): KLispSexp
}