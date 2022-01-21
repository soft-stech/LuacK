/*******************************************************************************
 * Copyright (c) 2009-2011 Luaj.org. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.luaj.vm2.lib

import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs

/** Abstract base class for Java function implementations that take one argument and
 * return one value.
 *
 *
 * Subclasses need only implement [LuaValue.call] to complete this class,
 * simplifying development.
 * All other uses of [.call], [.invoke],etc,
 * are routed through this method by this class,
 * dropping or extending arguments with `nil` values as required.
 *
 *
 * If more than one argument are required, or no arguments are required,
 * or variable argument or variable return values,
 * then use one of the related function
 * [ZeroArgFunction], [TwoArgFunction], [ThreeArgFunction], or [VarArgFunction].
 *
 *
 * See [LibFunction] for more information on implementation libraries and library functions.
 * @see .call
 * @see LibFunction
 *
 * @see ZeroArgFunction
 *
 * @see TwoArgFunction
 *
 * @see ThreeArgFunction
 *
 * @see VarArgFunction
 */
/** Default constructor  */
abstract class OneArgFunction : LibFunction() {

    abstract override fun call(arg: LuaValue): LuaValue

    override fun call(): LuaValue {
        return call(LuaValue.NIL)
    }

    override fun call(arg1: LuaValue, arg2: LuaValue): LuaValue {
        return call(arg1)
    }

    override suspend fun suspendableCall(arg1: LuaValue, arg2: LuaValue): LuaValue {
        return call(arg1, arg2)
    }

    override fun call(arg1: LuaValue, arg2: LuaValue, arg3: LuaValue): LuaValue {
        return call(arg1)
    }

    override fun invoke(args: Varargs): Varargs {
        return call(args.arg1())
    }

    override suspend fun invokeSuspend(args: Varargs): Varargs{
        return invoke(args)
    }
} 
