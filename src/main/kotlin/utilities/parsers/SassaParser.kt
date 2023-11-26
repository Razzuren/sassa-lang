package utilities.parsers

data class Statement(val tokens: List<Token>, val statementType: StatementType)

sealed class StatementType {
    object Main : StatementType()
    object If : StatementType()
    object Else : StatementType()
    object Loop : StatementType()
    object Out : StatementType()
    object In : StatementType()
    object Return : StatementType()
    object VariableDeclaration : StatementType()
    object Assignment : StatementType()
    object Block : StatementType()
    object FunctionDeclaration : StatementType()
    object Call : StatementType()
    object Invalid : StatementType()
}

class SassaParser(private val debug: Boolean){
    private var currentIndex = 0
    private lateinit var tokens: List<Token>
    private var statements = mutableListOf<Statement>()

    fun parse(tokens: List<Token>): Map<String, Any> {
        this.tokens = tokens
        val resultMap = HashMap<String, Any>()
        try {
            parseMain()
            resultMap["exitcode"] = 0
            resultMap["parsed_statements"] = statements
        } catch (e: Exception) {
            resultMap["exitcode"] = 1
            resultMap["parsed_statements"] = listOf(
                Statement(listOf(Token(TokenType.String,e.message!!,0,0)),
                StatementType.Invalid))
        } finally {
            //Should it here? No. Will it stay here? ATM yes.
            return resultMap
        }
    }

    //<MAIN> := <COMMENT> <FUNCTIONS> <COMMENT> 'main' <BLOCK>
    private fun parseMain() {
       while (match(TokenType.Comment) || match(TokenType.NewLine)) advance()
        val auxIndex = currentIndex
        parseFunctions()
        consume(TokenType.Keyword, "Expected 'main'")
        statements.add(Statement(tokens.subList(auxIndex, currentIndex), StatementType.Main))
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
        statements.add(Statement(tokens.subList(auxIndex, currentIndex), StatementType.FunctionDeclaration))
        parseBlock()
    }

    //<HEADER> := <TYPE> <IDENTIFIER> | <TYPE> <IDENTIFIER> ',' [<HEADER> not empty] |  empty
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
        val currentStatement = if (statements.isNotEmpty()) statements.size else 1
        consume(TokenType.OpenBrace, "Expected '{' at the beginning of a block")
        while (!match(TokenType.CloseBrace)) {
            parseCommands()
        }
        val secondBracket = currentIndex
        consume(TokenType.CloseBrace, "Expected '}' at the end of a block")
        //Insert the block statement on the right place
        statements.add(currentStatement,Statement(tokens.subList(firstBracket, secondBracket), StatementType.Block))
    }

    //<COMANDS> :=<COMMENT> <COMMAND> <COMMENT> '/n' <COMMANDS> | empty
    private fun parseCommands() {
        while (match(TokenType.Comment) || match(TokenType.NewLine)) advance()
        parseCommand()
        while (match(TokenType.Comment)) advance()
        //if there is a new line, parse the next command
        if (!match(TokenType.NewLine)) {
            consume(TokenType.NewLine, "Expected new line")
        }
        while(match(TokenType.NewLine)){
            consume(TokenType.NewLine, "Expected new line")
            parseCommand()
        }
    }

    //<COMMAND>:= <IF> | <LOOP> | <VARIABLE_DECLARATION> | <CUSTOM_FUNCTION> | <OUTPUT> | <ATTRIBUTION> | <RETURN>
    private fun parseCommand() {
        if (match(TokenType.Keyword)) {
            when (getCurrentToken().text) {
                "if" -> parseIfStatement()
                "loop" -> parseLoopStatement()
                "out" -> parseOutStatement()
                "return" -> parseReturnStatement()
                else ->  throw ParseException("On line " + getCurrentToken().line + ", Column "
                        + getCurrentToken().column + " "
                        + "found invalid keyword: " + getCurrentToken().text)
            }
        } else if (match(TokenType.Type)) {
            parseVariableDeclaration()
        } else if (match(TokenType.Identifier)) {
            parseAttributionOrFunctionCall()
        } else if (match(TokenType.Comment)) advance()
    }

    //<RETURN> := 'return' <EXPRESSION>
    private fun parseReturnStatement() {
        val auxIndex = currentIndex
        consume(TokenType.Keyword, "Expected 'return'")
        parseExpression()
        statements.add(Statement(tokens.subList(auxIndex, currentIndex), StatementType.Return))
    }

    //<loop> = 'loop' '(' [<CONDITION> | <FOR_CONDITION> |  empty ] ')' <BLOCK>
    private fun parseLoopStatement() {
        val auxIndex = currentIndex
        consume(TokenType.Keyword, "Expected 'loop'")
        consume(TokenType.OpenParenthesis, "Expected '('")
        if (match(TokenType.Type)) {
            parseForCondition()
        } else if (match(TokenType.Identifier)) {
            try {
                if (tokens[currentIndex + 1].type == TokenType.ForCondition) {
                    parseForCondition()
                } else {
                    parseCondition()
                }
            } catch (e: Exception) {
                throw e
            }
        } else if (match(TokenType.LogicalOperator)) {
            parseCondition()
        }
        consume(TokenType.CloseParenthesis, "Expected ')'")
        statements.add(Statement(tokens.subList(auxIndex, currentIndex), StatementType.Loop))
        parseBlock()
    }

    //<FOR_CONDITION := [<IDENTIFIER> | <VARIABLE_DECLARATION>] '..' <EXPRESSION> [ ':' <EXPRESSION> | empty ]
    private fun parseForCondition() {
        if (match(TokenType.Type)) {
            parseType()
            parseIdentifier()
            consume(TokenType.Equals, "Expected '='")
            parseExpression()
        } else {
            parseIdentifier()
        }
        consume(TokenType.ForCondition, "Expected '..'")
        parseExpression()
        if (match(TokenType.ForStep)) {
            consume(TokenType.ForStep, "Expected ':'")
            parseExpression()
        }
    }

    //<OUTPUT> := 'out' '(' <EXPRESSION> ')'
    private fun parseOutStatement() {
        val auxIndex = currentIndex
        consume(TokenType.Keyword, "Expected 'out'")
        consume(TokenType.OpenParenthesis, "Expected '('")
        parseExpression()
        consume(TokenType.CloseParenthesis, "Expected ')'")
        statements.add(Statement(tokens.subList(auxIndex, currentIndex), StatementType.Out))
    }

    //<VARIABLE_DECLARATION> := <TYPE> <IDENTIFIER> '=' <EXPRESSION>
    private fun parseVariableDeclaration() {
        val auxIndex = currentIndex
        parseType()
        parseIdentifier()
        consume(TokenType.Equals, "Expected '='")
        parseExpression()
        statements.add(Statement(tokens.subList(auxIndex, currentIndex), StatementType.VariableDeclaration))
    }

    //<ATTRIBUTION> := <IDENTIFIER> '=' <EXPRESSION>
    //<CUSTOM_FUNCTION> := <IDENTIFIER> '(' <ARGUMENTS> ')'
    private fun parseAttributionOrFunctionCall() {
        val auxIndex = currentIndex
        val currentStatement = if (statements.isNotEmpty()) statements.size else 1
        parseIdentifier()
        if (match(TokenType.Equals)) {
            consume(TokenType.Equals, "Expected '='")
            parseExpression()
            statements.add(currentStatement,Statement(tokens.subList(auxIndex, currentIndex), StatementType.Assignment))
        } else {
            consume(TokenType.OpenParenthesis, "Expected '('")
            parseArguments()
            consume(TokenType.CloseParenthesis, "Expected ')'")
            statements.add(currentStatement,Statement(tokens.subList(auxIndex, currentIndex), StatementType.Call))
        }
    }

    //<CUSTOM_FUNCTION> := <IDENTIFIER> '(' <ARGUMENTS> ')'
    private fun parseFunctionCall() {
        val auxIndex = currentIndex
        val currentStatement = if (statements.isNotEmpty()) statements.size else 1
        parseIdentifier()
        consume(TokenType.OpenParenthesis, "Expected '('")
        parseArguments()
        consume(TokenType.CloseParenthesis, "Expected ')'")
    }

    //<ARGUMENTS> := <EXPRESSION> | <EXPRESSION> ',' <ARGUMENTS>
    private fun parseArguments() {
        parseExpression()
        while (match(TokenType.Comma)) {
            consume(TokenType.Comma, "Expected ','")
            parseExpression()
        }
    }

    // 'if' '(' <CONDITION> ')' <BLOCK> <ELSE>
    private fun parseIfStatement() {
        val auxIndex = currentIndex
        consume(TokenType.Keyword, "Expected 'if'")
        consume(TokenType.OpenParenthesis, "Expected '('")
        parseCondition()
        consume(TokenType.CloseParenthesis, "Expected ')'")
        statements.add(Statement(tokens.subList(auxIndex, currentIndex), StatementType.If))
        parseBlock()
        parseElseStatement()
    }

    //<ELSE> := 'else' <BLOCK>
    private fun parseElseStatement() {
        if (match(TokenType.Keyword) && getCurrentToken().text == "else") {
            val auxIndex = currentIndex
            consume(TokenType.Keyword, "Expected 'else'")
            statements.add(Statement(tokens.subList(auxIndex, currentIndex), StatementType.Else))
            parseBlock()
        }
    }

    //<CONDITION>   '==' | '!=' | '>=' |'<=' | '<'  | '>'  | '&&' |  '||' |  '^'  between <EXPRESSION> and <EXPRESSION>
    private fun parseCondition() {
        parseExpression()
        while (match(TokenType.LogicalOperator)) {
            consume(TokenType.LogicalOperator, "Expected logical operator")
            parseExpression()
        }
    }

    //<EXPRESSION> := <EXPRESSION> '+' <TERM> | <EXPRESSION> '-' <TERM> | <TERM>
    private fun parseExpression() {
        parseTerm()
        while (match(TokenType.NumericalOperator)) {
            consume(TokenType.NumericalOperator, "Expected numerical operator")
            parseTerm()
        }
    }

    //<TERM> := <TERM> '*' <FACTOR> | <TERM> '/' <FACTOR> | <TERM> '%' <FACTOR> | <FACTOR>
    private fun parseTerm() {
        parseFactor()
        while (match(TokenType.NumericalOperator)) {
            consume(TokenType.NumericalOperator, "Expected numerical operator")
            parseFactor()
        }
    }

    //<FACTOR>   := '-'<FACTOR> | '!'<FACTOR> | <VALUE>
    private fun parseFactor() {
        if (match(TokenType.NumericalOperator)) {
            consume(TokenType.NumericalOperator, "Expected numerical operator")
            parseFactor()
        } else if (match(TokenType.LogicalOperator)) {
            consume(TokenType.LogicalOperator, "Expected logical operator")
            parseFactor()
        } else {
            parseValue()
        }
    }

    //<VALUE> := <NUMBER> | <STRING> | <BOOLEAN> | <IDENTIFIER> | <CUSTOM_FUNCTION> | <INPUT> | '(' <EXPRESSION> ')'
    private fun parseValue() {
        if (match(TokenType.Number)) {
            consume(TokenType.Number, "Expected number")
        } else if (match(TokenType.String)) {
            consume(TokenType.String, "Expected string")
        } else if (match(TokenType.Boolean)) {
            consume(TokenType.Boolean, "Expected boolean")
        } else if (match(TokenType.Identifier)) {
            if (tokens[currentIndex + 1].type == TokenType.OpenParenthesis) parseFunctionCall()
            else parseIdentifier()
        } else if (match(TokenType.Keyword) && getCurrentToken().text == "in") {
            parseInputStatement()
        }
        else {
            consume(TokenType.OpenParenthesis, "Expected '('")
            parseExpression()
            consume(TokenType.CloseParenthesis, "Expected ')'")
        }
    }

    //<INPUT> :=  'in' '(' <STRING> ')'
    private fun parseInputStatement() {
        consume(TokenType.Keyword, "Expected 'in'")
        consume(TokenType.OpenParenthesis, "Expected '('")
        parseExpression()
        consume(TokenType.CloseParenthesis, "Expected ')'")
    }

    private fun consume(type: TokenType, errorMessage: String) {
        if (match(type)) {
            if (debug) println("now consuming: $type ${getCurrentToken().text}")
            advance()
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
