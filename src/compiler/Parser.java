package compiler;

import java.util.ArrayList;

public class Parser {
	
	static String lookahead;
	static ArrayList<Token> tokens;
	static SymbolTable currTable;
	
	static String idenListType = "";
	static String idenListKind = "";
	static String idenListMode = "";
	static String idenListId = "";
	static int label = 0;
	static boolean parseError = false;
	static int parameterNum = 0;

	
	static ArrayList<Symbol> listOfParameters = new ArrayList<Symbol>();
	
	public static void parser(ArrayList<Token> tkns) throws Exception{
		SymanticAnalyzer.init();
		tokens = tkns;
		lookahead = tokens.get(0).token;
		SystemGoal();
		SymanticAnalyzer.close();
		if (!parseError) {
			System.out.println("Program successfully parsed");
		} else {
			System.out.println("Program parsed with errors");
		}
	}
	
	public static void SystemGoal() throws Exception{
		Program();
		match("MP_EOF");
	}


	public static void Program() throws Exception{
		ProgramHeading();
		match("MP_SCOLON");
		Block(true);
		match("MP_PERIOD");
	}
	
	public static void ProgramHeading() throws Exception{
		match("MP_PROGRAM");
		currTable = new SymbolTable(tokens.get(0).lexeme, label++);
		ProgramIdentifier();
		SymanticAnalyzer.write("PUSH D0\n");
		SymanticAnalyzer.write("MOV SP D0\n");
		SymanticAnalyzer.write("BR L0\n");
	}
	
	public static void Block(boolean isProgram) throws Exception{
		VariableDeclarationPart();
		// generate code to allocate stack space for local variables and properly offset the SP
		
		ProcedureAndFunctionDeclarationPart();
		SymanticAnalyzer.write(currTable.getLabel() + ":\n");
		if(isProgram) {
			SymanticAnalyzer.programDeclaration(currTable.nestingLevel, currTable.getNumOfNonParams() + 2, "L"+label);
		} else {
			SymanticAnalyzer.programDeclaration(currTable.nestingLevel, currTable.getNumOfNonParams(), "L"+label);
		}
		StatementPart();
		SymanticAnalyzer.programDestroy(currTable.nestingLevel, currTable.getNumOfNonParams());
		currTable = currTable.destroy();
	}
	
	public static void VariableDeclarationPart() throws Exception{
		if(lookahead == "MP_VAR"){
			idenListKind = "variable";
			idenListMode = "copy";
			match("MP_VAR");
			VariableDeclaration();
			match("MP_SCOLON");
			VariableDeclarationTail();
			
		}else if(lookahead == "MP_BEGIN" || lookahead == "MP_FUNCTION" || lookahead == "MP_PROCEDURE" ){
			//epsilon
			
		}else{
			throw new Exception("Parse Error " + tokens.get(0).lineNumber + ":" + tokens.get(0).colNumber + ": Found " + lookahead + ", Expected MP_VAR, MP_BEGIN, MP_FUNCTION, or MP_PROCEDURE");
		}
	}
	
	public static void VariableDeclarationTail() throws Exception{
		if(lookahead == "MP_IDENTIFIER"){
			VariableDeclaration();
			match("MP_SCOLON");
			VariableDeclarationTail();
		}else if(lookahead == "MP_BEGIN" || lookahead == "MP_FUNCTION" || lookahead == "MP_PROCEDURE" ){
			//epsilon
		}else{
			throw new Exception("Parse Error " + tokens.get(0).lineNumber + ":" + tokens.get(0).colNumber + ": Found " + lookahead + ", Expected MP_IDENTIFIER, MP_BEGIN, MP_FUNCTION, or MP_PROCEDURE");
		}
	}
	
	public static void VariableDeclaration() throws Exception{
		int currentPos = currTable.symbols.size();
		IdentifierList();
		match("MP_COLON");
		Type();
		//update the type for all identifiers just added to the symbolTable
		for(int i = currentPos; i < currTable.symbols.size(); i++){
			currTable.symbols.get(i).type = idenListType;
		}
	}
	
	public static void Type() throws Exception{
		switch(lookahead){
		case "MP_INTEGER":
			match("MP_INTEGER");
			break;
		case "MP_STRING":
			match("MP_STRING");
			break;
		case "MP_BOOLEAN":
			match("MP_BOOLEAN");
			break;
		case "MP_FIXED":
			match("MP_FIXED");
			break;
		case "MP_FLOAT":
			match("MP_FLOAT");
			break;
		default:
			throw new Exception("Parse Error " + tokens.get(0).lineNumber + ":" + tokens.get(0).colNumber + ": Found " + lookahead + ", expected MP_INTEGER, MP_STRING, MP_BOOLEAN, MP_FIXED, or MP_FLOAT");
		}
	}
	
	public static void ProcedureAndFunctionDeclarationPart() throws Exception{
		if(lookahead == "MP_FUNCTION"){
			FunctionDeclaration();
			ProcedureAndFunctionDeclarationPart();
			
		}else if(lookahead == "MP_PROCEDURE"){
			ProcedureDeclaration();
			ProcedureAndFunctionDeclarationPart();
		}else if(lookahead == "MP_BEGIN"){
			//epsilon
		}else{
			throw new Exception("Parse Error " + tokens.get(0).lineNumber + ":" + tokens.get(0).colNumber + ": Found " + lookahead + ", expected MP_BEGIN, MP_FUNCTION, or MP_PROCEDURE");
		}
	}
	
	public static void ProcedureDeclaration() throws Exception{
		ProcedureHeading();
		match("MP_SCOLON");
		Block(false);
		match("MP_SCOLON");
	}
	
	public static void FunctionDeclaration() throws Exception{
		FunctionHeading();
		match("MP_SCOLON");
		Block(false);
		match("MP_SCOLON");
	}
	
	public static void ProcedureHeading() throws Exception{
		match("MP_PROCEDURE");
		idenListId = tokens.get(0).lexeme;
		currTable = new SymbolTable(idenListId, label++, currTable);
		ProcedureIdentifier();
		listOfParameters.clear();
		OptionalFormalParameterList();
		// insert into parent table after we know all the parameters listOfParameters
		currTable.parent.insert(new Symbol(idenListId, "", "procedure", listOfParameters, currTable.parent.symbols.size(), currTable.label));
	}
	
	public static void FunctionHeading() throws Exception{
		match("MP_FUNCTION");
		idenListId = tokens.get(0).lexeme;
		currTable = new SymbolTable(idenListId, label++, currTable);
		FunctionIdentifier();
		listOfParameters.clear();
		OptionalFormalParameterList();
		match("MP_COLON");
		idenListType = lookahead;
		Type();
		currTable.parent.insert(new Symbol(idenListId, idenListType, "function", listOfParameters, currTable.parent.symbols.size(), currTable.label));
	}
	
	public static void OptionalFormalParameterList() throws Exception{
		if(lookahead == "MP_LPAREN"){
			match("MP_LPAREN");
			FormalParameterSection();
			FormalParameterSectionTail();
			match("MP_RPAREN");
		}else if(lookahead == "MP_COLON" || lookahead == "MP_SCOLON"){
			//epsilon
		}else{
			throw new Exception("Parse Error " + tokens.get(0).lineNumber + ":" + tokens.get(0).colNumber + ": Found " + lookahead + ", expected MP_LPAREN, MP_COLON, or MP_SCOLON");
		}
	}
	
	public static void FormalParameterSectionTail() throws Exception{
		if(lookahead == "MP_SCOLON"){
			match("MP_SCOLON");
			FormalParameterSection();
			FormalParameterSectionTail();			
		}else if(lookahead == "MP_RPAREN"){
			//epsilon
		}else{
			throw new Exception("Parse Error " + tokens.get(0).lineNumber + ":" + tokens.get(0).colNumber + ": Found " + lookahead + ", expected MP_RPAREN, or MP_SCOLON");
		}
	}
	
	public static void FormalParameterSection() throws Exception{
		if(lookahead == "MP_IDENTIFIER"){
			ValueParameterSection();
		}else if(lookahead == "MP_VAR"){
			VariableParameterSection();
		}else{
			throw new Exception("Parse Error " + tokens.get(0).lineNumber + ":" + tokens.get(0).colNumber + ": Found " + lookahead +", expected MP_IDENTIFER or MP_VAR");
		}
	}
	
	public static void ValueParameterSection() throws Exception{
		int currentPos = currTable.symbols.size();
		
		idenListMode = "copy";
		idenListKind = "parameter";
		
		IdentifierList();
		match("MP_COLON");
		idenListType = lookahead;
		Type();
		
		// update the type for all identifiers just added to the symbolTable
		for(int i = currentPos; i < currTable.symbols.size(); i++){
			currTable.symbols.get(i).type = idenListType;
		}
		
		// update the types of the parameter list for the parent table
		for(int i = 0; i < listOfParameters.size(); i++) {
			if(listOfParameters.get(i).type == null) {
				listOfParameters.get(i).type = idenListType;
			}
		
		}
	}
	
	public static void VariableParameterSection() throws Exception{
		match("MP_VAR");
		
		idenListMode = "ref";
		idenListKind = "parameter";
		
		int currentPos = currTable.symbols.size();
		IdentifierList();
		match("MP_COLON");
		idenListType = lookahead;
		Type();
		
		// update the type for all identifiers just added to the symbolTable
		for(int i = currentPos; i < currTable.symbols.size(); i++){
			currTable.symbols.get(i).type = idenListType;
		}
		
		// update the types of the parameter list for the parent table
		for(int i = 0; i < listOfParameters.size(); i++) {
			listOfParameters.get(i).type = idenListType;
		}
	}
	
	public static void StatementPart() throws Exception{
		CompoundStatement();
	}
	
	public static void CompoundStatement() throws Exception{
		match("MP_BEGIN");
		StatementSequence();
		match("MP_END");
	}
	
	public static void StatementSequence() throws Exception{
		try {
			Statement();
		} catch (Exception e) {
			System.out.println(e);
			
			while (!tokens.get(0).token.equals("MP_SCOLON")) {
				tokens.remove(0);
			}
			parseError = true;
			lookahead = tokens.get(0).token;
		} finally {
			StatementTail();
		}
	}
	
	public static void StatementTail() throws Exception{
		if(lookahead == "MP_SCOLON"){
			match("MP_SCOLON");
			try{
				Statement();
			}catch(Exception e){
				System.out.println(e);
				while(!tokens.get(0).token.equals("MP_SCOLON")){
					tokens.remove(0);
				}
				parseError = true;
				lookahead = tokens.get(0).token;
			}finally{
				StatementTail();
			}
		}else if(lookahead == "MP_UNTIL" || lookahead == "MP_END"){
			//epsilon
		}else{
			throw new Exception("Parse Error " + tokens.get(0).lineNumber + ":" + tokens.get(0).colNumber + ": Found " + lookahead + ", expected MP_UNTIL, MP_END, or MP_SCOLON");
		}
	}
	
	public static void Statement() throws Exception{
		switch(lookahead){
		case "MP_BEGIN":
			CompoundStatement();
			break;
		case "MP_READ":
			ReadStatement();
			break;
		case "MP_WRITE":
			WriteStatement();
			break;
		case "MP_WRITELN":
			WriteStatement();
			break;
		case "MP_IDENTIFIER":
			// double lookahead to determine if we wnat to expand Assignment Statement or Procedure Statement
			if(tokens.get(1).token.equals("MP_ASSIGN")) {
				AssignmentStatement();
			} else {
				ProcedureStatement();
			}
			break;
		case "MP_IF":
			IfStatement();
			break;
		case "MP_WHILE":
			WhileStatement();
			break;
		case "MP_REPEAT":
			RepeatStatement();
			break;
		case "MP_FOR":
			ForStatement();
			break;
		default:
			EmptyStatement();
			break;
		}
	}
	
	/********************
	 JOURDANS SECTION 
	 ********************/
	
	public static void EmptyStatement(){	
		//Epsilon
	}
	
	public static void ReadStatement() throws Exception{
		match("MP_READ");
		match("MP_LPAREN");
		ReadParameter();
		ReadParameterTail();
		match("MP_RPAREN");
	}
	
	public static void ReadParameterTail() throws Exception{
		if(lookahead == "MP_COMMA"){
			match("MP_COMMA");
			ReadParameter();
			ReadParameterTail();
		}else if(lookahead == "MP_RPAREN"){
			//epsilon
		}else{
			throw new Exception("Parse Error " + tokens.get(0).lineNumber + ":" + tokens.get(0).colNumber + ": Found " + lookahead + ", expected MP_RPAREN, or MP_COMMA");
		}
	}
	
	public static void ReadParameter() throws Exception{
		SymanticAnalyzer.readStatement(tokens.get(0).lexeme, currTable);
		VariableIdentifier();
	}
	
	public static void WriteStatement() throws Exception{
		if(lookahead == "MP_WRITE"){
			match("MP_WRITE");
			match("MP_LPAREN");
			WriteParameter(false);
			WriteParameterTail(false);
			match("MP_RPAREN");
		}else if(lookahead == "MP_WRITELN"){ //Does not exist yet..
			match("MP_WRITELN");
			match("MP_LPAREN");
			WriteParameter(true);
			WriteParameterTail(true);
			match("MP_RPAREN");
		}else{
			throw new Exception("Parse Error " + tokens.get(0).lineNumber + ":" + tokens.get(0).colNumber + ": Found " + lookahead + ", expected MP_WRITE, or MP_WRITELN");
		}
	}
	
	public static void WriteParameterTail(Boolean writeLine) throws Exception{
		if(lookahead == "MP_COMMA"){
			match("MP_COMMA");
			WriteParameter(writeLine);
			WriteParameterTail(writeLine);
		}else if(lookahead == "MP_RPAREN"){
			//epsilon
		}else{
			throw new Exception("Parse Error " + tokens.get(0).lineNumber + ":" + tokens.get(0).colNumber + ": Found " + lookahead + ", expected MP_RPAREN, or MP_COMMA");
		}
	}
	
	public static void WriteParameter(Boolean writeLn) throws Exception{
		OrdinalExpression(-1, null);
		SymanticAnalyzer.writeStatement(writeLn);
	}
	
	public static void AssignmentStatement() throws Exception{
		Token token = tokens.get(0);
		String resultType = currTable.getTypeByLexeme(token.lexeme);
		int[] offset = currTable.getOffsetByLexeme(token.lexeme);
		boolean isFunction = currTable.isFunction(token.lexeme);
		match("MP_IDENTIFIER");
		match("MP_ASSIGN");
		String exprType = Expression(-1, null);
		Symbol s = currTable.getSymbolByLexeme(token.lexeme);
		if (s == null) {
			throw new Exception(token.lineNumber + ":" + token.colNumber + " " + token.lexeme + " is not defined");
		}
		if (isFunction) {
			//function
			int[] input = {0, currTable.nestingLevel};
			SymanticAnalyzer.assignByReference(resultType, exprType, input);
		} else if (s.kind == "parameter" && s.mode == "ref") {
			// ref
			SymanticAnalyzer.assignByReference(resultType, exprType, currTable.getOffsetByLexeme(token.lexeme));
		} else {
			//copy
			SymanticAnalyzer.assign(resultType, exprType, offset);
		}
		
	}
	
	public static void IfStatement() throws Exception{
		match("MP_IF");
		if(BooleanExpression() == "MP_BOOLEAN"){
			match("MP_THEN");
			//two labels, one to branch to else part, one to branch to finish of else part
			String branchLable = "L" + label++;
			String branchAfterElse = "L" + label++;
			SymanticAnalyzer.write("BRFS "+ branchLable + "\n");
			Statement();
			//if statement was executed branch to after the else part
			SymanticAnalyzer.write("BR "+ branchAfterElse + "\n");
			SymanticAnalyzer.write(branchLable + ":\n");
			OptionalElsePart();
			SymanticAnalyzer.write(branchAfterElse + ":\n");
		}else{
			throw new Exception("Parse Error " + tokens.get(0).lineNumber + ":" + tokens.get(0).colNumber + ": Not a boolean expression");
		}
	}
	
	public static void OptionalElsePart() throws Exception{
		if(lookahead == "MP_ELSE"){
			match("MP_ELSE");
			Statement();
		}else if(lookahead == "MP_END" || lookahead == "MP_UNTIL" || lookahead == "MP_SCOLON"){
			//epsilon
		}else{
			throw new Exception("Parse Error " + tokens.get(0).lineNumber + ":" + tokens.get(0).colNumber + ": Found " + lookahead + ", expected MP_ELSE, MP_END, MP_UNTIL, or MP_SCOLON");
		}
	}
	
	public static void RepeatStatement() throws Exception{
		match("MP_REPEAT");
		String startLabel = "L" + label++;
		SymanticAnalyzer.write(startLabel + ":\n");
		StatementSequence();
		match("MP_UNTIL");
		BooleanExpression();
		SymanticAnalyzer.write("BRFS "+ startLabel + "\n");
	}
	
	public static void WhileStatement() throws Exception{
		match("MP_WHILE");
		String checkLabel = "L" + label++;
		String endLabel = "L" + label++;
		SymanticAnalyzer.write(checkLabel + ":\n");
		BooleanExpression();
		SymanticAnalyzer.write("BRFS "+ endLabel + "\n");
		match("MP_DO");
		Statement();
		SymanticAnalyzer.write("BR " + checkLabel + "\n");
		SymanticAnalyzer.write(endLabel + ":\n");
	}
	
	public static void ForStatement() throws Exception{
		match("MP_FOR");
		String finishLabel = "L" + label++;
		String startLabel = "L" + label++;
		//get offset and type of control variable
		int[] offset = currTable.getOffsetByLexeme(tokens.get(0).lexeme);
		String resultType = currTable.getTypeByLexeme(tokens.get(0).lexeme);
		ControlVariable();
		match("MP_ASSIGN");
		String exprType = InitialValue();
		//assign the init value to the control variable
		SymanticAnalyzer.assign(resultType, exprType, offset);
		Boolean stepUp = StepValue();
		//jump here every iteration
		SymanticAnalyzer.write(startLabel + ":\n");
		String finalType = FinalValue();
		//finalval is ontop of stack, push control variable to compare
		SymanticAnalyzer.write("PUSH " + offset[0] + "(D" + offset[1] + ")\n");
		if(stepUp){
			SymanticAnalyzer.computeExpression(resultType, finalType, "CMPLTS");
		}else{
			SymanticAnalyzer.computeExpression(resultType, finalType, "CMPGTS");
		}
		//if equal go to finishlabel
		SymanticAnalyzer.write("BRTS " + finishLabel + "\n");
		//else do the statement again
		match("MP_DO");
		Statement();
		if(stepUp){
			SymanticAnalyzer.write("PUSH " + offset[0] + "(D" + offset[1] + ")\n");
			SymanticAnalyzer.write("PUSH #1\n");
			SymanticAnalyzer.computeExpression(resultType, "MP_INTEGER", "ADDS");
			SymanticAnalyzer.write("POP " + offset[0] + "(D" + offset[1] + ")\n");
		}else{
			SymanticAnalyzer.write("PUSH " + offset[0] + "(D" + offset[1] + ")\n");
			SymanticAnalyzer.write("PUSH #1\n");
			SymanticAnalyzer.computeExpression(resultType, "MP_INTEGER", "SUBS");
			SymanticAnalyzer.write("POP " + offset[0] + "(D" + offset[1] + ")\n");
		}
		SymanticAnalyzer.write("BR " + startLabel + "\n");
		SymanticAnalyzer.write(finishLabel + ":\n");
		
	}
	
	public static void ControlVariable() throws Exception{
		VariableIdentifier();
	}
	
	public static String InitialValue() throws Exception{
		return OrdinalExpression(-1, null);
	}
	
	public static Boolean StepValue() throws Exception{
		if(lookahead == "MP_TO"){
			match("MP_TO");
			return true;
		}else if(lookahead == "MP_DOWNTO"){
			match("MP_DOWNTO");
			return false;
		}else{
			throw new Exception("Parse Error " + tokens.get(0).lineNumber + ":" + tokens.get(0).colNumber + ": Found " + lookahead + ", expected MP_DOWNTO or MP_TO");
		}
	}
	
	public static String FinalValue() throws Exception{
		return OrdinalExpression(-1, null);
	}
	
	public static void ProcedureStatement() throws Exception{
		String procedureName = tokens.get(0).lexeme;
		ProcedureIdentifier();
		SymanticAnalyzer.procedureFunctionDeclaration(currTable.nestingLevel + 1, currTable, procedureName);
		OptionalActualParameterList(procedureName);
		SymanticAnalyzer.procedureFunctionDestroy(currTable.nestingLevel + 1, currTable, procedureName);
	}
	
	public static void OptionalActualParameterList(String name) throws Exception{
		parameterNum = 0;
		if(lookahead == "MP_LPAREN"){
			match("MP_LPAREN");
			ActualParameter(name);
			ActualParameterTail(name);
			match("MP_RPAREN");
		}else if(lookahead == "MP_AND" || lookahead == "MP_DIV" ||lookahead == "MP_DO" ||lookahead == "MP_DOWNTO" || lookahead == "MP_ELSE" ||lookahead == "MP_END" ||lookahead == "MP_MOD" ||lookahead == "MP_OR" ||lookahead == "MP_THEN" ||lookahead == "MP_TO" ||lookahead == "MP_UNTIL" ||lookahead == "MP_COMMA" ||lookahead == "MP_EQUAL" ||lookahead == "MP_FLOAT_DIV" ||lookahead == "MP_GEQUAL" ||lookahead == "MP_GTHAN" |lookahead == "MP_LEQUAL" ||lookahead == "MP_LTHAN" ||lookahead == "MP_MINUS" ||lookahead == "MP_NEQUAL" ||lookahead == "MP_PLUS" ||lookahead == "MP_RPAREN" ||lookahead == "MP_SCOLON" ||lookahead == "MP_TIMES" ){
				//epsilon
		}else{
			throw new Exception("Parse Error " + tokens.get(0).lineNumber + ":" + tokens.get(0).colNumber + ": Found " + lookahead + ", expected just about anything else");
		}
		if (currTable.isFunction(name) || currTable.isProcedure(name)) {
			SymanticAnalyzer.updateStackPointer(currTable.nestingLevel, currTable, name);
			SymanticAnalyzer.write("CALL L" + currTable.getLabelByLexeme(name) + "\n");
		}
	}
	
	public static void ActualParameterTail(String name) throws Exception{
		if(lookahead == "MP_COMMA"){
			match("MP_COMMA");
			ActualParameter(name);
			ActualParameterTail(name);
		}else if(lookahead == "MP_RPAREN"){
			//epsilon
		}else{
			throw new Exception("Parse Error " + tokens.get(0).lineNumber + ":" + tokens.get(0).colNumber + ": Found " + lookahead + ", expected MP_COMMA, or MP_RPAREN");
		}
	}
	
	public static void ActualParameter(String name) throws Exception{
		OrdinalExpression(parameterNum, name);
		parameterNum++;
	}
	
	
	public static String Expression(int parameterNum, String name) throws Exception{
		String expressionType = SimpleExpression(parameterNum, name);
		Boolean relationalPart = OptionalRelationalPart(expressionType, parameterNum, name);
		if(relationalPart){
			return "MP_BOOLEAN";
		}else{
			return expressionType;
		}
	}
	
	public static Boolean OptionalRelationalPart(String expressionType, int parameterNum, String name) throws Exception{
		Boolean returnVal = false;
		if(lookahead == "MP_EQUAL" || lookahead == "MP_GTHAN" || lookahead == "MP_LTHAN" || lookahead == "MP_LEQUAL" || lookahead == "MP_GEQUAL" || lookahead == "MP_NEQUAL"){
			String operator = RelationalOperator();
			String secondExpressionType = SimpleExpression(parameterNum, name);
			SymanticAnalyzer.computeExpression(expressionType, secondExpressionType, operator);
			returnVal = true;
		}else if(lookahead == "MP_DO" ||lookahead == "MP_DOWNTO" || lookahead == "MP_ELSE" ||lookahead == "MP_END"||lookahead == "MP_THEN" ||lookahead == "MP_TO" ||lookahead == "MP_UNTIL" ||lookahead == "MP_COMMA" ||lookahead == "MP_RPAREN" ||lookahead == "MP_SCOLON"){
			//epsilon
		}else{
			throw new Exception("Parse Error " + tokens.get(0).lineNumber + ":" + tokens.get(0).colNumber + ": Found " + lookahead + ", expected just about anything else");
		}
		return returnVal;
	}
	
	public static String RelationalOperator() throws Exception{
		String returnVal = null;
		switch(lookahead){
		case "MP_EQUAL":
			match("MP_EQUAL");
			returnVal = "CMPEQS";
			break;
		case "MP_LTHAN":
			match("MP_LTHAN");
			returnVal = "CMPLTS";
			break;
		case "MP_GTHAN":
			match("MP_GTHAN");
			returnVal = "CMPGTS";
			break;
		case "MP_LEQUAL":
			match("MP_LEQUAL");
			returnVal = "CMPLES";
			break;
		case "MP_GEQUAL":
			match("MP_GEQUAL");
			returnVal = "CMPGES";
			break;
		case "MP_NEQUAL":
			match("MP_NEQUAL");
			returnVal = "CMPNES";
			break;
		default:
			throw new Exception("Parse Error " + tokens.get(0).lineNumber + ":" + tokens.get(0).colNumber + ": Found " + lookahead + ", expected boolean operator");
		}
		return returnVal;
	}

	
	/****************
	 * DOMS SECTION
	 ****************/
	public static String SimpleExpression(int parameterNum, String name) throws Exception {
		Boolean hasMinus = OptionalSign();
		String termType = Term(parameterNum, name);
		if(hasMinus) {
			SymanticAnalyzer.pushLiteralVal("-1");
			SymanticAnalyzer.computeExpression(termType, "MP_INTEGER", "MULS");
		}		
		TermTail(termType, parameterNum, name);
		return termType;
	}
	
	public static void TermTail(String termType, int parameterNum, String name) throws Exception {	
		switch(lookahead) {
		case "MP_PLUS":
		case "MP_MINUS":
		case "MP_OR":
			String operator =  AddingOperator();
			String termTailType = Term(parameterNum, name);
			SymanticAnalyzer.computeExpression(termType, termTailType, operator);
			TermTail(termType, parameterNum, name);
			break;
		case "MP_DO":
		case "MP_DOWNTO":
		case "MP_ELSE":
		case "MP_END":
		case "MP_THEN":
		case "MP_TO":
		case "MP_UNTIL":
		case "MP_COMMA":
		case "MP_EQUAL":
		case "MP_GEQUAL":
		case "MP_GTHAN":
		case "MP_LEQUAL":
		case "MP_LTHAN":
		case "MP_NEQUAL":
		case "MP_RPAREN":
		case "MP_SCOLON":
			// epsilon
			break;
		default:
			throw new Exception("Parse Error " + tokens.get(0).lineNumber + ":" + tokens.get(0).colNumber + ": Found " + lookahead + ", expected just about anything else");
		}
	}
	
	public static Boolean OptionalSign() throws Exception {
		Boolean returnVal = false;
		switch(lookahead) {
		case "MP_PLUS":
			match("MP_PLUS");
			break;
		case "MP_MINUS":
			match("MP_MINUS");
			returnVal = true;
			break;
		case "MP_FALSE":
		case "MP_NOT":
		case "MP_TRUE":
		case "MP_IDENTIFIER":
		case "MP_INTEGER_LIT":
		case "MP_FIXED_LIT":
		case "MP_FLOAT_LIT":
		case "MP_STRING_LIT":
		case "MP_LPAREN":	
			//epsilon
			break;
		default:
			throw new Exception("Parse Error " + tokens.get(0).lineNumber + ":" + tokens.get(0).colNumber + ": Found " + lookahead + ", expected MP_PLUS, MP_MINUS, MP_FALSE, MP_NOT, MP_TRUE, MP_IDENTIFIER, MP_INTEGER_LIT, MP_FIXED_LIT, MP_FLOAT_LIT, MP_STRING_LIT, OR MP_LPAREN");
		}
		return returnVal;
	}

	public static String AddingOperator() throws Exception {
		String returnVal = null;
		switch(lookahead) {
		case "MP_PLUS":
			match("MP_PLUS");
			returnVal = "ADDS";
			break;
		case "MP_MINUS":
			match("MP_MINUS");
			returnVal = "SUBS";
			break;
		case "MP_OR":
			match("MP_OR");
			returnVal = "ORS";
			break;
		default:
			throw new Exception("Parse Error " + tokens.get(0).lineNumber + ":" + tokens.get(0).colNumber + ": Found " + lookahead + ", expected MP_PLUS, MP_MINUS, or MP_OR");
		}
		return returnVal;
	}
	
	public static String Term(int parameterNum, String name) throws Exception {
		String factorType = Factor(parameterNum, name);
		FactorTail(factorType, parameterNum, name);
		return factorType;
	}
	
	public static void FactorTail(String factorType, int parameterNum, String name) throws Exception {
		switch(lookahead) {
		case "MP_AND":
		case "MP_DIV":
		case "MP_MOD":
		case "MP_FLOAT_DIV":
		case "MP_TIMES":		
			String operator = MultiplyingOperator();
			String factorTailType = Factor(parameterNum, name);
			SymanticAnalyzer.computeExpression(factorType, factorTailType, operator);
			FactorTail(factorType, parameterNum, name);
			break;
		case "MP_DO":
		case "MP_DOWNTO":
		case "MP_ELSE":
		case "MP_END":
		case "MP_OR":
		case "MP_THEN":
		case "MP_TO":
		case "MP_UNTIL":
		case "MP_COMMA":
		case "MP_EQUAL":
		case "MP_GEQUAL":
		case "MP_GTHAN":
		case "MP_LEQUAL":
		case "MP_LTHAN":
		case "MP_MINUS":
		case "MP_NEQUAL":
		case "MP_PLUS":
		case "MP_RPAREN":
		case "MP_SCOLON":
			//epsilon
			break;
		default:
			throw new Exception("Parse Error " + tokens.get(0).lineNumber + ":" + tokens.get(0).colNumber + ": Found " + lookahead + ", expected just about anything else");
		}
	}
	
	public static String MultiplyingOperator() throws Exception {
		String returnVal = null;
		switch(lookahead) {
		case "MP_TIMES":
			match("MP_TIMES");
			returnVal = "MULS";
			break;
		case "MP_FLOAT_DIV":
			match("MP_FLOAT_DIV");
			returnVal = "DIVSF";
			break;
		case "MP_DIV":
			match("MP_DIV");
			returnVal = "DIVS";
			break;
		case "MP_MOD":
			match("MP_MOD");
			returnVal = "MODS";
			break;
		case "MP_AND":
			match("MP_AND");
			returnVal = "ANDS";
			break;
		default:
			throw new Exception("Parse Error " + tokens.get(0).lineNumber + ":" + tokens.get(0).colNumber + ": Found " + lookahead + ", expceted MP_TIMES, MP_FLOAT_DIV, MP_DIV, MP_MOD, or MP_AND");
		}
		return returnVal;
	}
	
	public static String Factor(int parameterNum, String procedureName) throws Exception {
		String returnVal = null;
		switch(lookahead) {
		case "MP_INTEGER_LIT":
			SymanticAnalyzer.pushLiteralVal(tokens.get(0).lexeme);			
			match("MP_INTEGER_LIT");
			returnVal = "MP_INTEGER";
			break;
		case "MP_FIXED_LIT":
			SymanticAnalyzer.pushLiteralVal(tokens.get(0).lexeme);			
			match("MP_FIXED_LIT");
			returnVal = "MP_FIXED";
			break;
		case "MP_FLOAT_LIT":
			SymanticAnalyzer.pushLiteralVal(tokens.get(0).lexeme);			
			match("MP_FLOAT_LIT");
			returnVal = "MP_FLOAT";
			break;
		case "MP_STRING_LIT":
			SymanticAnalyzer.pushLiteralVal("\"" + tokens.get(0).lexeme + "\"");
			match("MP_STRING_LIT");
			returnVal = "MP_STRING";
			break;
		case "MP_TRUE":
			SymanticAnalyzer.pushLiteralVal("1");
			match("MP_TRUE");
			returnVal = "MP_BOOLEAN";
			break;
		case "MP_FALSE":
			SymanticAnalyzer.pushLiteralVal("0");
			match("MP_FALSE");
			returnVal = "MP_BOOLEAN";
			break;
		case "MP_NOT":
			match("MP_NOT");
			Factor(parameterNum, procedureName);
			SymanticAnalyzer.write("NOTS\n");
			returnVal = "MP_BOOLEAN";
			break;
		case "MP_LPAREN":
			match("MP_LPAREN");
			returnVal = Expression(parameterNum, procedureName);
			match("MP_RPAREN");
			break;
		case "MP_IDENTIFIER":			
			returnVal = currTable.getTypeByLexeme(tokens.get(0).lexeme);
			String ID_name = tokens.get(0).lexeme;
			boolean isFunction = currTable.isFunction(ID_name);
			if (isFunction) {
				SymanticAnalyzer.procedureFunctionDeclaration(currTable.nestingLevel + 1, currTable, ID_name);
			} else {
				// pass in by copy or reference
				if(parameterNum != -1 && currTable.getSymbolByLexeme(procedureName).parameterList.get(parameterNum).kind == "ref") {
					SymanticAnalyzer.pushRegisterByReference(ID_name, currTable);

				} else if (currTable.getSymbolByLexeme(ID_name).mode == "ref") {
					SymanticAnalyzer.pushRegisterRef(ID_name, currTable);
				} else {
				
					// by copy
					SymanticAnalyzer.pushRegisterVal(ID_name, currTable);
				}
			}
			FunctionIdentifier();
			OptionalActualParameterList(ID_name);	
			if(isFunction){
				SymanticAnalyzer.procedureFunctionDestroy(currTable.nestingLevel + 1, currTable, ID_name);
				SymanticAnalyzer.pushRegisterVal(ID_name, currTable);
			}
			break;
		default:
			throw new Exception("Parse Error " + tokens.get(0).lineNumber + ":" + tokens.get(0).colNumber + ": Found " + lookahead + ", expected MP_INTEGER_LIT, MP_FIXED_LIT, MP_FLOAT_LIT, MP_STRING_LIT, MP_TRUE, MP_FALSE, MP_NOT, MP_LPAREN, or MP_IDENTIFIER");
		}
		return returnVal;
	}
		
	public static void ProgramIdentifier() throws Exception {
		match("MP_IDENTIFIER");
	}
	
	public static void VariableIdentifier() throws Exception {
		match("MP_IDENTIFIER");
	}
	
	public static void ProcedureIdentifier() throws Exception {
		match("MP_IDENTIFIER");
	}
	
	public static void FunctionIdentifier() throws Exception {	
		match("MP_IDENTIFIER");
	}
	
	public static String BooleanExpression() throws Exception {
		return Expression(-1, null);
	}
	
	public static String OrdinalExpression(int parameterNum, String name) throws Exception {
		return Expression(parameterNum, name);
	}
	
	public static void IdentifierList() throws Exception {
		// add the first one
		currTable.insert(new Symbol(tokens.get(0).lexeme, idenListType, idenListKind, idenListMode, currTable.symbols.size()));
		listOfParameters.add(new Symbol(null, idenListMode));
		match("MP_IDENTIFIER");
		IdentifierTail();
	}
	
	public static void IdentifierTail() throws Exception {
		switch(lookahead) {
		case "MP_COMMA":
			match("MP_COMMA");
			// add the rest
			currTable.insert(new Symbol(tokens.get(0).lexeme, idenListType, idenListKind, idenListMode, currTable.symbols.size()));
			listOfParameters.add(new Symbol(null, idenListMode));
			match("MP_IDENTIFIER");
			IdentifierTail();
			break;
		case "MP_COLON":
			//epsilon
			break;
		default:
			throw new Exception("Parse Error " + tokens.get(0).lineNumber + ":" + tokens.get(0).colNumber + ": Found " + lookahead + ", expected MP_COMMA or MP_COLON");
		}	
	}
	
	
	public static void match(String token) throws Exception{
		if(lookahead.equals(token)) {
			idenListType = tokens.get(0).token;
			tokens.remove(0);
//			System.out.println(lookahead + " matched");
			if(tokens.size() > 0){
				lookahead = tokens.get(0).token;
				
				while(true){
					if(lookahead == "MP_COMMENT"){
						tokens.remove(0);
						lookahead = tokens.get(0).token;
						System.out.println("Comment removed");
					}else{
						break;
					}
				}
			}
		}else{
			throw new Exception("Parse Error " + tokens.get(0).lineNumber + ":" + tokens.get(0).colNumber + ": Found " + lookahead + ", expected " + token);
		}
	}
}





