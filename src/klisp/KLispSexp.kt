package klisp

import java.lang.IndexOutOfBoundsException
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

    constructor(integer: Long) : this(integer, 1L)

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

abstract class KLispList : KLispSexp(), Iterable<KLispSexp> {
    companion object {
        val NIL = object : KLispList() {
            override fun eval(env: KLispEnv) = this
            override fun car() = this
            override fun cdr() = this
            override fun size() = 0
            override fun get(at: Int) = this
            override fun subList(start: Int) = this

            override fun iterator() = object : Iterator<KLispSexp> {
                override fun hasNext() = false
                override fun next(): KLispSexp {
                    throw NoSuchElementException("Invalid iterator for $this")
                }
            }

            override fun toString() = "nil"
        }


    }

    abstract fun car(): KLispSexp
    abstract fun cdr(): KLispSexp
    abstract fun size(): Int
    abstract operator fun get(at: Int): KLispSexp
    abstract fun subList(start: Int): KLispList

    fun toSeqList(): KLispList {
        var current: KLispList = this
        val list = mutableListOf<KLispSexp>()

        while(current != NIL) {
            list.add(current.car())
            current = current.cdr() as? KLispList ?: throw KLispException("Not a null ended list")
        }

        return KLispSeqList.createList(list)
    }
}

open class KLispSeqList private constructor(private val list: List<KLispSexp>) : KLispList() {
    companion object {
        fun createList(list: List<KLispSexp>): KLispList {
            return if(list.isEmpty()) {
                NIL
            } else {
                KLispSeqList(list)
            }
        }
    }


    override fun eval(env: KLispEnv): KLispSexp {
        return env.apply(this)
    }

    override fun car() = list[0]
    override fun cdr() = subList(1)
    override fun size() = list.size
    override operator fun get(at: Int) = list[at]
    override fun subList(start: Int) = createList(list.subList(start, list.size))

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

open class KLispCons(private val car: KLispSexp, private val cdr: KLispSexp) : KLispList() {
    companion object {
        fun createList(list: List<KLispSexp>): KLispList {
            return if(list.isEmpty()) {
                NIL
            } else {
                list.reversed().fold(NIL) { acc: KLispList, elem: KLispSexp ->
                    KLispCons(elem, acc)
                }
            }
        }
    }

    override fun eval(env: KLispEnv): KLispSexp {
        return env.apply(this)
    }

    override fun car() = car
    override fun cdr() = cdr

    override fun size(): Int {
        var size = 0

        val iter = this.iterator()
        while(iter.hasNext()) {
            iter.next()
            size++
        }

        return size
    }

    override fun get(at: Int): KLispSexp {
        var currentPosition = 0

        val iter = this.iterator()
        while(iter.hasNext()) {
            val item = iter.next()
            if(currentPosition == at) {
                return item
            }
            currentPosition++
        }

        throw IndexOutOfBoundsException()
    }

    override fun subList(start: Int): KLispList {
        var currentPosition = 0
        var currentCons: KLispList = this

        while(currentCons != NIL) {
            if(currentPosition == start) {
                return currentCons
            }

            currentCons = currentCons.cdr() as? KLispList ?: throw KLispException("Invalid list")
            currentPosition++
        }

        throw IndexOutOfBoundsException()
    }

    override fun iterator() = KLispConsIterator(this)

    override fun toString(): String {
        val builder = StringBuilder()
        builder.append("(")

        var currentCons = this
        while(true) {
            builder.append(currentCons.car())
            builder.append(" ")

            val cdr = currentCons.cdr()

            if(cdr == NIL) {
                break
            } else if(cdr is KLispCons) {
                currentCons = cdr
            } else {
                builder.append(". ")
                builder.append(currentCons.cdr().toString())
                builder.append(" ")
                break
            }
        }

        builder.setLength(builder.length-1) // remove last space
        builder.append(")")

        return builder.toString()
    }
}

class KLispConsIterator(private var currentHead: KLispList) : Iterator<KLispSexp> {
    override fun hasNext() = currentHead != KLispList.NIL

    override fun next(): KLispSexp {
        val item = currentHead.car()
        currentHead = currentHead.cdr() as? KLispList ?: throw KLispException("Invalid list")

        return item
    }
}

abstract class KLispLambda : KLispValue("lambda #$lambdaNumber") {
    companion object {
        private var lambdaNumber = 0
    }

    abstract operator fun invoke(args: KLispList): KLispSexp

    init {
        lambdaNumber++
    }
}