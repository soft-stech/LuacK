package SerializableObjects

import org.luaj.vm2.LuaClosure
import org.luaj.vm2.LuaString
import org.luaj.vm2.LuaValue
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

class SerializableExecutionLuaStack : java.io.Serializable {
    /**
     * Время создания стэка исполнения, считается временем начала исполнения скрипта
     */
    private val scriptStartTime: Long = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);

    /**
     * Стэк исполнения, содержащий в себе стэки вложенных друг в друга lua-функций
     */
    private var closureStacks: Stack<SerializableLuaClosureStack> =
        Stack<SerializableLuaClosureStack>()

    /**
     * Текущий уровень вложенности, на котором исполняется lua-функция, начиная с нулевого
     */
    private var currentLevel : Int = 0

    /**
     * Функция для кеширования значения мнимого уровня вложенности, на котором мы фиксируем исполнение
     * java-кода. По умолчанию максимально возможный
     */
    private var javaLevel = Int.MAX_VALUE

    /**
     * Возвращаемое значение, если такое требуется вернуть из java-функции
     */
    private var returnValue = LuaValue.NIL

    /**
     * Отметка о том что пользователь положил трубку
     */
    var userEndCall = false

    /**
     * Функция, вызываемая при завершении зваонка пользователем
     */
    var userEndCallClosure: LuaClosure? = null

    fun getScriptStartTime(): Long {
        return scriptStartTime
    }

    fun getCurrentLevel() : Int {
        return currentLevel
    }

    fun setCurrentLevel(level: Int) {
        currentLevel = level
    }

    fun getClosureStacks(): Stack<SerializableLuaClosureStack> {
        return closureStacks
    }

    fun getJavaLevel(): Int {
        return javaLevel
    }

    fun setJavaLevel(value: Int) {
        javaLevel = value
    }

    fun getReturnValue(): LuaValue {
        return returnValue
    }

    fun SetReturnValue(valueOf: LuaString) {
        returnValue = valueOf
    }

}
