package com.redhat.ceylon.compiler.typechecker.tree;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.antlr.runtime.CommonToken;
import org.antlr.runtime.Token;

import com.redhat.ceylon.compiler.typechecker.analyzer.AnalysisError;
import com.redhat.ceylon.compiler.typechecker.analyzer.AnalysisWarning;
import com.redhat.ceylon.compiler.typechecker.analyzer.UsageWarning;
import com.redhat.ceylon.compiler.typechecker.model.Scope;
import com.redhat.ceylon.compiler.typechecker.model.Unit;
import com.redhat.ceylon.compiler.typechecker.parser.LexError;
import com.redhat.ceylon.compiler.typechecker.parser.ParseError;
import com.redhat.ceylon.compiler.typechecker.util.PrintVisitor;

public abstract class Node {
    
    private String text;
    private Token token;
    private Token endToken;
    private Scope scope;
    private Unit unit;
    private List<Message> errors = new ArrayList<Message>();
    private List<Node> children = new ArrayList<Node>();
    
    protected Node(Token token) {
        this.token = token;
    }
    
    /**
     * The scope within which the node occurs. 
     */
    public Scope getScope() {
        return scope;
    }

    public void setScope(Scope scope) {
        this.scope = scope;
    }
    
    /**
     * The compilation unit in which the node
     * occurs.
     */
    public Unit getUnit() {
        return unit;
    }
    
    public void setUnit(Unit unit) {
        this.unit = unit;
    }
    
    /**
     * The text of the corresponding ANTLR node.
     */
    public String getText() {
    	if (text!=null) {
    		return text;
    	}
    	else if (token==null) {
    		return "";
    	}
    	else if (endToken==null) {
    		return token.getText();
    	}
    	else {
    		return token.getText() + endToken.getText();
    	}
    }
    
    public void setText(String text) {
        this.text = text;
    }
    
    /**
     * The text of the corresponding ANTLR tree node. Never null, 
     * since the two trees are isomorphic.
     */
    public Token getToken() {
    	return getFirstChildToken();
    }
    
    public Token getMainToken() {
        return token;
    }
    
    public String getLocation() {
    	Token token = getToken();
    	Token endToken = getEndToken();
		if (token==null) {
    		return "unknown location";
    	}
    	else if (endToken==null) {
    		return toLocation(token);
    	}
    	else {
    		return toLocation(token) + "-" + 
    				toEndLocation(endToken);
    	}
    }
    
    public Integer getStartIndex() {
    	Token token = getToken();
    	if (token==null) {
    		return null;
    	}
    	else {
    		return ((CommonToken) token).getStartIndex();
    	}
    }

    public Integer getStopIndex() {
    	Token token = getEndToken();
    	if (token==null) {
    		token = getToken();
    	}
    	if (token==null) {
    		return null;
    	}
    	else {
    		return ((CommonToken) token).getStopIndex();
    	}
    }

	private static String toLocation(Token token) {
		return token.getLine() + ":" + 
				token.getCharPositionInLine();
	}
    
	private static String toEndLocation(Token token) {
		return token.getLine() + ":" + 
				(token.getCharPositionInLine()
				+ token.getText().length()-1);
	}
    
    private boolean isMissingToken(Token t) {
        return t.getText().startsWith("<missing ");
    }
    
    private Token getFirstChildToken() {
        Token token=this.token==null || 
                //the tokens ANTLR inserts to represent missing tokens
                //don't come with useful offset information
                isMissingToken(this.token) ?
                null : this.token;
		for (Node child: getChildren()) {
			Token tok = child.getFirstChildToken();
			if (tok!=null && (token==null || 
					tok.getTokenIndex()<token.getTokenIndex())) {
				token=tok;
			}
		}
		return token;
    }

    private Token getLastChildToken() {
		Token token=this.endToken==null || 
		        //the tokens ANTLR inserts to represent missing tokens
		        //don't come with useful offset information
		        isMissingToken(endToken) ?
				this.token : this.endToken;
		for (Node child: getChildren()) {
			Token tok = child.getLastChildToken();
			if (tok!=null && (token==null || 
			        tok.getTokenIndex()>token.getTokenIndex())) {
				token=tok;
			}
		}
		return token;
    }
    
    public Token getEndToken() {
    	return getLastChildToken();
	}
    
    public void setEndToken(Token endToken) {
        //the tokens ANTLR inserts to represent missing tokens
        //don't come with useful offset information
        if (endToken==null || !isMissingToken(endToken)) {
            this.endToken = endToken;
        }
	}
    
    /**
     * The compilation errors belonging to this node.
     */
    public List<Message> getErrors() {
        return errors;
    }
    
    public void addError(String message) {
        errors.add( new AnalysisError(this, message) );
    }
    
    public void addError(String message, int code) {
        errors.add( new AnalysisError(this, message, code) );
    }
    
    public void addUnexpectedError(String message) {
        errors.add( new UnexpectedError(this, message) );
    }
    
    public void addWarning(String message) {
        errors.add( new AnalysisWarning(this, message) );
    }

    public void addUsageWarning(String message) {
        errors.add( new UsageWarning(this, message) );
    }

    public void addParseError(ParseError error) {
        errors.add(error);
    }
    
    public void addLexError(LexError error) {
        errors.add(error);
    }
    
    public abstract void visit(Visitor visitor);
    
    public abstract void visitChildren(Visitor visitor);
    
    @Override
    public String toString() {
        StringWriter w = new StringWriter();
        PrintVisitor pv = new PrintVisitor(w);
        pv.visitAny(this);
        return w.toString();
        //return getClass().getSimpleName() + "(" + text + ")"; 
    }

    public String getNodeType() {
        return getClass().getSimpleName();
    }
    
    public void handleException(Exception e, Visitor visitor) {
	    addError(getMessage(e, visitor));
    }

    public String getMessage(Exception e, Visitor visitor) {
		return visitor.getClass().getSimpleName() +
	            " caused an exception visiting " + getNodeType() + 
	            " node: " + e + " at " + getLocationInfo(e);
	}

	private String getLocationInfo(Exception e) {
		return e.getStackTrace().length>0 ? 
				e.getStackTrace()[0].toString() : "unknown";
	}
	
	public void connect(Node child) {
		if (child!=null) {
			children.add(child);
		}
	}

	public List<Node> getChildren() {
		return children;
	}

}
