package utilities.parsers

data class Statement(val tokens: List<Token>, val statementType: StatementType, val children: List<Statement>)

sealed class StatementType {
    object Main : StatementType()
    object If : StatementType()
    object Else : StatementType()
    object Loop : StatementType()
    object Condition : StatementType()
    object ForCondition : StatementType()
    object Out : StatementType()
    object In : StatementType()
    object Return : StatementType()
    object VariableDeclaration : StatementType()
    object Assignment : StatementType()
    object Expression : StatementType()
    object Block : StatementType()
    object FunctionDeclaration : StatementType()
    object Call : StatementType()
    object Parameter : StatementType()
}

class SassaParser(val debug: Boolean){
    private var currentIndex = 0
    private var currentLine = 0
    private lateinit var tokens: List<Token>
    private var statements = mutableListOf<Statement>()
    private lateinit var auxToken: Token

    fun parse(tokens: List<Token>): Map<String, Any> {
        this.tokens = tokens
        val resultMap = HashMap<String, Any>()
        try {
            parseMain()
            resultMap["exitcode"] = 0
            resultMap["parsed_statements"] = statements
        } catch (e: Exception) {
            println(e.message)
            resultMap["exitcode"] = 1
            resultMap["parsed_statements"] = e.message ?: "Parsing error"
        }
        return resultMap
    }

    //<MAIN> := <COMMENT> <FUNCTIONS> <COMMENT> 'main' <BLOCK>
    private fun parseMain() {
       while (match(TokenType.Comment) || match(TokenType.NewLine)) advance()
        parseFunctions()
        consume(TokenType.Keyword, "Expected 'main'")
        statements.add(Statement(listOf(getCurrentToken()), StatementType.Main, listOf()))
        parseBlock()
    }

    //<FUNCTIONS> := <COMMENT> <FUNCTION> <FUNCTIONS>
    private fun parseFunctions() {
        while (match(TokenType.Comment) || match(TokenType.NewLine)) advance()
        if(match(TokenType.Type)) {
            parseFunctionDeclaration()
            parseFunctions()
        }
    }

    //<FUNCTION> := <TYPE> <IDENTIFIER> '(' <HEADER> ')' <BLOCK>
    private fun parseFunctionDeclaration() {
        val auxIndex = currentIndex
        parseType()
        parseIdentifier()
        consume(TokenType.OpenParenthesis, "Expected '('")
        parseHeader()
        consume(TokenType.CloseParenthesis, "Expected ')'")
        statements.add(Statement(tokens.subList(auxIndex, currentIndex), StatementType.FunctionDeclaration, listOf()))
        parseBlock()
    }

    //<HEADER> := <TYPE> <IDENTIFIER> | <TYPE> <IDENTIFIER> ',' [<HEADER> ∌ ε] |  ε
    private fun parseHeader() {
        parseType()
        parseIdentifier()
        while (match(TokenType.Comma)) {
            consume(TokenType.Comma, "Expected ','")
            parseType()
            parseIdentifier()
        }
    }

    //<TYPE> := 'num' | 'str' | 'bool' | 'any'
    private fun parseType() {
        if (match(TokenType.Type)) {
            consume(TokenType.Type, "Expected type")
        }
    }

    //<IDENTIFIER> := [a-z]+ [a-z A-Z 0-9]* not part of reserved words
    private fun parseIdentifier() {
        if (match(TokenType.Identifier)) {
            consume(TokenType.Identifier, "Expected identifier")
        }
    }

    //<BLOCK> := '{' <COMMANDS> '}'
    private fun parseBlock() {
        val firstBracket = currentIndex
        val currentStatement = if (statements.isNotEmpty()) statements.size - 1 else 0
        consume(TokenType.OpenBrace, "Expected '{' at the beginning of a block")
        while (!match(TokenType.CloseBrace)) {
            parseCommands()
        }
        val secondBracket = currentIndex
        consume(TokenType.CloseBrace, "Expected '}' at the end of a block")
        val statement = Statement(tokens.subList(firstBracket, secondBracket), StatementType.Block,
            statements.subList(currentStatement, statements.size))
        //Insert the block statement on the right place
        statements.add(currentStatement,statement)
    }

    //<COMMENT> <COMMAND> '/n' <COMMANDS> | <COMMAND> '/n'
    private fun parseCommands() {
        while (match(TokenType.Comment)) advance()
        parseCommand()
        if (match(TokenType.NewLine)) {
            consume(TokenType.NewLine, "Expected new line")
            parseCommands()
        }
    }

    //<COMMAND>:= <IF> | <LOOP> | <VARIABLE_DECLARATION> | <CUSTOM_FUNCTION> | <OUTPUT> | <ATTRIBUTION> | <RETURN>
    private fun parseCommand() {
        if (match(TokenType.Keyword)) {
            when (getCurrentToken().text) {
                "if" -> parseIfStatement()
                //todo()"loop" -> parseLoopStatement()
                //todo()  "out" -> parseOutStatement()
                //todo()  "in" -> parseInStatement()
                //todo()  "return" -> parseReturnStatement()
            }
        } else if (match(TokenType.Type)) {
            //todo()parseVariableDeclaration()
        } else if (match(TokenType.Identifier)) {
            //todo()  parseAssignmentOrCustomFunction()
        }
    }

    // 'if' '(' <CONDITION> ')' <BLOCK> <ELSE>
    private fun parseIfStatement() {
        val auxIndex = currentIndex
        consume(TokenType.Keyword, "Expected 'if'")
        consume(TokenType.OpenParenthesis, "Expected '('")
        //todo() parseCondition()
        consume(TokenType.CloseParenthesis, "Expected ')'")
        statements.add(Statement(tokens.subList(auxIndex, currentIndex), StatementType.If, listOf()))
        parseBlock()
        //todo() parseElseStatement()
    }

    private fun parseExpression() {
        if (match(TokenType.Identifier)) {
        } else {
            throw ParseException("Expected identifier in expression")
        }
    }

    private fun consume(type: TokenType, errorMessage: String) {
        if (match(type)) {
            advance()
            println("now consuming: $type")
        } else {
            throw ParseException("On line " + getCurrentToken().line + ", Column "
                    + getCurrentToken().column + " "
                    + errorMessage + ", found: " + getCurrentToken().text)
        }
    }

    private fun advance() {
        currentIndex++
    }

    private fun getCurrentToken(): Token {
        if (currentIndex < tokens.size) {
            return tokens[currentIndex]
        }
        throw ParseException("Unexpected end of input")
    }

    private fun match(expectedType: TokenType): Boolean {
        return currentIndex < tokens.size && tokens[currentIndex].type == expectedType
    }

    private class ParseException(message: String) : Exception(message)

}
