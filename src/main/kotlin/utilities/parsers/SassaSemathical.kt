import utilities.parsers.Statement
import utilities.parsers.StatementType
import utilities.parsers.Token
import utilities.parsers.TokenType

data class SymbolTable(val symbols: HashMap<String, SymbolRow>)

data class SymbolRow(val type: SymbolType, val value: String)
sealed class SymbolType {
    object Variable : SymbolType()
    object Function : SymbolType()
}

class SassaSemantical(private val debug: Boolean = false) {
    private val symbolTable = SymbolTable(hashMapOf())
    private var currentIndex: Int = 0
    private val statements = ArrayList<Statement>()
    private var currentScope = "main"

    fun analyze(statements: List<Statement>): String {
        val resultMap = HashMap<String, Any>()
        this.statements.addAll(statements)
        if (debug) println("statements size: ${statements.size}")
        try {
            for (statement in statements) {
                if (debug) println("now analyzing: ${statement.statementType} at index $currentIndex")
                when (statement.statementType) {
                    StatementType.FunctionDeclaration -> analyzeFunctionDeclaration(statement)
                    StatementType.Main -> analyseMain(statement)
                    StatementType.VariableDeclaration -> analyzeVariableDeclaration(statement)
                    StatementType.Assignment -> analyseAssignment(statement)
                    StatementType.Block -> {}
                    StatementType.Call -> analyzeFunctionCall(statement)
                    StatementType.If -> analyseIf(statement)
                    StatementType.Else -> analyzeElse(statement)
                    StatementType.In -> analyseIn(statement)
                    StatementType.Loop -> analyzeLoop(statement)
                    StatementType.Out -> analyseOut(statement)
                    StatementType.Return -> analyseReturn(statement)
                    else -> throw SemanticException("Invalid statement")
                }
                currentIndex++
            }
            var code = SassaCodeGenerator(debug).generateCode(statements, symbolTable)
            if (debug) println(code)
            return code
        } catch (e: SemanticException) {
            if (debug) throw e
            return e.message!!

        }


    }

    private fun analyseReturn(statement: Statement) {
        if (statement.tokens[1].type == TokenType.Identifier) {
            if (!symbolTable.symbols.containsKey("${statement.tokens[1].text}_${currentScope}")) {
                throw SemanticException("Variable ${statement.tokens[1].text} not declared")
            }
        }
    }

    private fun analyseOut(statement: Statement) {
        if (statement.tokens[2].type != TokenType.String) {
            if (!symbolTable.symbols.containsKey("${statement.tokens[2].text}_${currentScope}")) {
                throw SemanticException("Variable ${statement.tokens[2].text} not declared")
            }
        }
    }

    private fun analyzeLoop(statement: Statement) {
        try {
            if (statements[currentIndex + 1].statementType != StatementType.Block) {
                throw SemanticException("Loop statement must have a body")
            }
        } catch (e: IndexOutOfBoundsException) {
            throw SemanticException("Loop statement must have a body")
        }
    }

    private fun analyseIn(statement: Statement) {
        if (statement.tokens[2].type != TokenType.String) {
            if (!symbolTable.symbols.containsKey("${statement.tokens[2].text}_${currentScope}")) {
                throw SemanticException("Variable ${statement.tokens[2].text} not declared")
            }
        }
    }

    private fun analyzeElse(statement: Statement) {
        try {
            if (statements[currentIndex + 1].statementType != StatementType.Block) {
                throw SemanticException("Else statement must have a body")
            }
        } catch (e: IndexOutOfBoundsException) {
            throw SemanticException("Else statement must have a body")
        }
    }

    private fun analyseIf(statement: Statement) {
        if (statement.tokens[3].text == ")") {
            throw SemanticException("If statement must have a condition")
        }
        try {
            if (statements[currentIndex + 1].statementType != StatementType.Block) {
                throw SemanticException("If statement must have a body")
            }
        } catch (e: IndexOutOfBoundsException) {
            throw SemanticException("If statement must have a body")
        }
    }

    private fun analyzeFunctionCall(statement: Statement) {
        val functionName = statement.tokens[0].text
        var key = "${functionName}_${currentScope}"
        if (!symbolTable.symbols.containsKey(key)) {
            throw SemanticException("Function $functionName not declared")
        }
    }

    private fun analyseAssignment(statement: Statement) {
        val variableName = statement.tokens[0].text
        val key = "${variableName}_${currentScope}"
        if (!symbolTable.symbols.containsKey(key)) {
            throw SemanticException("Variable $variableName not declared")
        }
        if (statement.tokens[2].type == TokenType.Identifier
            && !symbolTable.symbols.containsKey(key)
        ) {
            throw SemanticException("$variableName not declared")
        }
    }

    private fun analyseMain(statement: Statement) {
        currentScope = "main"
        if (symbolTable.symbols.containsKey("main")) {
            throw SemanticException("Main function already declared")
        }
        try {
            if (statements[currentIndex + 1].statementType != StatementType.Block) {
                throw SemanticException("Main function must have a body")
            }
        } catch (e: IndexOutOfBoundsException) {
            throw SemanticException("Main function must have a body")
        }
        symbolTable.symbols["main"] = SymbolRow(SymbolType.Function, "Any")
    }

    private fun analyzeFunctionDeclaration(statement: Statement) {
        val functionName = statement.tokens[1].text
        val type = statement.tokens[0].text
        val key = "${functionName}_main"
        var returnStatements: Int = 0
        if (symbolTable.symbols.containsKey(key)) throw SemanticException("Function $functionName already declared")

        try {
            if (statement.tokens[0].text != "any") {
                returnStatements = statements[currentIndex + 1].tokens.count { it.text == "return" }
                if (returnStatements == 0) throw SemanticException("Function $functionName must return a value")
            }
        } catch (e: IndexOutOfBoundsException) {
            throw SemanticException("Function $functionName must have a body")
        }

        symbolTable.symbols[key] = SymbolRow(SymbolType.Function, type)

        currentScope = functionName
        var arguments = statement.tokens.subList(2, statement.tokens.size)
        arguments.forEachIndexed { index, token ->
            if (token.type == TokenType.Identifier) {
                val key = "${token.text}_${currentScope}"
                symbolTable.symbols[key] = SymbolRow(SymbolType.Variable, arguments[index - 1].text)
            }
        }
    }

    private fun analyzeVariableDeclaration(statement: Statement) {
        val variableName = statement.tokens[1].text
        val type = statement.tokens[0].text
        val key = "${variableName}_${currentScope}"
        if (symbolTable.symbols.containsKey(key)) {
            throw SemanticException("Variable $variableName already declared")
        }
        val expectedType = statement.tokens[0].text
        val initializedWithType = statement.tokens[3].type
        if (!isValidInitialization(expectedType, initializedWithType)) {
            throw SemanticException("Variable $variableName must be initialized with a $expectedType")
        }
        symbolTable.symbols[key] = SymbolRow(SymbolType.Variable, type)
    }

    private fun isValidInitialization(expectedType: String, initializedWithType: TokenType): Boolean {
        if (initializedWithType ==TokenType.Keyword) return true
        val typeMap = mapOf(
            "num" to TokenType.Number,
            "str" to TokenType.String,
            "bool" to TokenType.Boolean
        )
        return typeMap[expectedType] == initializedWithType
    }
}

private class SassaCodeGenerator(private val debug: Boolean = false) {
    private var code = ""
    private var lastBlock = mutableListOf<Int>()
    private var currentLine = 0
    private lateinit var symbolTable: SymbolTable
    private var currentScope = "main"
    fun generateCode(statements: List<Statement>, symbolTable: SymbolTable): String {
        this.symbolTable = symbolTable
        symbolTable.symbols.forEach() { println(it) }
        for (statement in statements) {
            if (debug) {
                println("now writing: ${statement.statementType}")
                println("current line: $currentLine")
                if (lastBlock.isNotEmpty()) println("last block: ${lastBlock.last()}")
                println("current code: $code")
            }
            when (statement.statementType) {
                StatementType.FunctionDeclaration -> writeFunctionDeclaration(statement)
                StatementType.Main -> writeMain(statement)
                StatementType.VariableDeclaration -> writeVariableDeclaration(statement)
                StatementType.Assignment -> writeAssignment(statement)
                StatementType.Block -> writeBlock(statement)
                StatementType.Call -> writeFunctionCall(statement)
                StatementType.If -> writeIf(statement)
                StatementType.Else -> writeElse(statement)
                StatementType.In -> writeIn(statement)
                StatementType.Loop -> writeLoop(statement)
                StatementType.Out -> writeOut(statement)
                StatementType.Return -> writeReturn(statement)
                else -> throw SemanticException("Invalid statement")
            }
            currentLine = statement.tokens.last().line
            mustCloseBLock()
        }
        for (i in 0 until lastBlock.size) {
            code += "}\n"

        }
        code = code.trimIndent()
        code = code.replace("\n\n", "\n")
        code = code.replace("(,", "(")
        code = code.replace("\'", "\"")
        return code
    }

    private fun writeLoop(statement: Statement) {
        var isFor = false
        if (statement.tokens[2].type == TokenType.CloseParenthesis) {
            code += "while(true)"
        } else {
            for (token in statement.tokens.subList(2, statement.tokens.size)) {
                if (token.text == "..") {
                    isFor = true
                    break
                }
            }
            if (isFor) {
                code += "for("
                var condition = statement.tokens.subList(2, statement.tokens.size)

                for (token in condition) {
                    when (token.type) {
                         TokenType.ForStep -> {
                            code += " step "
                        }
                        TokenType.Equals -> {
                            code += " in "
                        }
                        TokenType.Type -> {}
                        TokenType.Identifier -> {
                            code += "${token.text}"
                        }

                        else -> code += token.text
                    }
                }
            } else {
                code += "while("
                var condition = statement.tokens.subList(2, statement.tokens.size)
                var conditionSnippet = condition.joinToString { it.text }
                conditionSnippet = conditionSnippet.replace("^", "xor")
                code += conditionSnippet
            }
        }

    }

    private fun writeElse(statement: Statement) {
        code += "else "
    }

    private fun mustCloseBLock() {
        if (lastBlock.isNotEmpty() && lastBlock.last() < currentLine) {
            var lines = code.lines().toMutableList()
            lines[lines.lastIndex] = "} ${lines.last()}"
            code = lines.joinToString("\n")
            lastBlock.removeAt(lastBlock.lastIndex)
        }
    }

    private fun writeReturn(statement: Statement) {
        code += "return "
        var expression = statement.tokens.subList(1, statement.tokens.size).joinToString { it.text }
        expression = expression.replace("^", "xor")
        expression = expression.replace("\'", "\"")
        code += expression
        code += "\n"
    }

    private fun writeOut(statement: Statement) {
        var expression = statement.tokens.subList(2, statement.tokens.size - 1)
        var expressionSnippet = "println(${expression.joinToString(" ") { it.text }})\n"
        expressionSnippet = expressionSnippet.replace("\'", "\"")
        code += expressionSnippet
    }

    private fun writeIn(statement: Statement) {
        var expression = statement.tokens.subList(4, statement.tokens.size - 1)
        val key = "${statement.tokens[0].text}_${currentScope}"
        val symbol = symbolTable.symbols[key]
        var expressionSnippet = "println(${expression.joinToString(" ") { it.text }})\n"
        expressionSnippet = expressionSnippet.replace("\'", "\"")
        code += expressionSnippet
        code += "${statement.tokens[0].text} = "
        code += "readLine()!!"
        if (symbol != null) {
            when (symbol.value) {
                "num" -> code += ".toDouble()"
                "bool" -> code += ".toBoolean()"
                else -> {}
            }
        }
        code += "\n"
    }

    private fun writeIf(statement: Statement) {
        code += "if ("
        var condition = statement.tokens.subList(2, statement.tokens.size - 1)

        condition.forEach() { token ->
            if (token.type == TokenType.Number) if(!token.text.contains(".")) token.text += ".0"
            if (token.text != "^")
                code += token.text
            else
                code += "xor"
        }

        code += ")\n"
    }

    private fun writeFunctionCall(statement: Statement) {
        code += "${statement.tokens[0].text}("
        var arguments = mutableListOf<String>()
        statement.tokens.subList(1,statement.tokens.size).forEachIndexed { index, token ->
            if (token.type == TokenType.Identifier) {
                arguments.add(token.text)
            }
        }
        var argumentsSize = arguments.size
        var index = 0
        arguments.forEach { argument ->
            code += argument
            if (index < argumentsSize - 1) {
                code += ", "
            }
            index++
        }
        code += ")\n"
    }

    private fun writeBlock(statement: Statement) {
        code += "{\n"
        lastBlock.add(statement.tokens.last().line)
    }

    private fun writeAssignment(statement: Statement) {
        var isInput = 0
        statement.tokens.forEach { token ->
            if (token.type == TokenType.Number) if(!token.text.contains(".")) token.text += ".0"
            if (token.text == "in") {
                isInput++
            }
        }
        if (isInput > 0) writeIn(statement)
        else {
            code += "${statement.tokens[0].text} = "
            var expression = statement.tokens.subList(2, statement.tokens.size).joinToString(" ") { it.text }
            expression = expression.replace("^", "xor")
            expression = expression.replace("\'", "\"")
            code += expression
            code += "\n"

        }
    }

    private fun writeVariableDeclaration(statement: Statement) {
        var expression = statement.tokens.subList(3, statement.tokens.size)
        expression.forEach() { token ->
            if (token.type == TokenType.Number) if(!token.text.contains(".")) token.text += ".0"
        }
        code += "var ${statement.tokens[1].text}"
        code += when (statement.tokens[0].text) {
            "num" -> " :Double"
            "str" -> " :String"
            "bool" -> " :Boolean"
            else -> " :Any"
        }
        code += " = "
        code += expression.joinToString(" ") { it.text }
        code += "\n"
    }


    private fun writeMain(statement: Statement) {
        currentScope = "main"
        code += "fun main()"
    }

    private fun writeFunctionDeclaration(statement: Statement) {
        currentScope = statement.tokens[1].text
        var arguments = statement.tokens.subList(2, statement.tokens.size)

        arguments = arguments.filter { it.type == TokenType.Identifier }

        var argumentsSize = arguments.size
        var index = 0
        code += "fun ${statement.tokens[1].text}("

        arguments.forEach { argument ->
            code += "${argument.text}: "
            when (symbolTable.symbols["${argument.text}_${currentScope}"]!!.value) {
                "num" -> code += "Double"
                "str" -> code += "String"
                "bool" -> code += "Boolean"
                "any" -> code += "Any"
            }
            if (index < argumentsSize - 1 && index < 1) {
                code += ", "
            }
            index++
        }
        code += ") :"
        when (statement.tokens[0].text) {
            "num" -> code += " Double"
            "str" -> code += " String"
            "bool" -> code += " Boolean"
            "any" -> code += " Any"
        }
    }
}

class SemanticException(message: String) : Exception(message)
