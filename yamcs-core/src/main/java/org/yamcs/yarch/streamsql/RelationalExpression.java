package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.CompiledExpression;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.DbReaderStream;
import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;

import org.yamcs.utils.parser.ParseException;

/**
 * Expressions of type "x &gt; y"
 * 
 * @author nm
 *
 */
public class RelationalExpression extends Expression {
    RelOp relOp;

    public RelationalExpression(Expression left, Expression right, RelOp relOp) throws ParseException {
        super(new Expression[] { left, right });
        this.relOp = relOp;
       // if (left.isConstant() && right.isConstant())
     //       constant = true;
    }

    public RelOp getRelation() {
        return relOp;
    }

    @Override
    public Expression addFilter(DbReaderStream tableStream) throws StreamSqlException {
        if ((children[1] instanceof ColumnExpression) && (children[0].isConstant())) {
            // swap left with right
            Expression tmp = children[1];
            children[1] = children[0];
            children[0] = tmp;
            relOp = relOp.getOppsite();
        }

        if ((children[0] instanceof ColumnExpression) && children[1].isConstant()) {
            ColumnExpression cexpr = (ColumnExpression) children[0];
            Object cvalue;
            if (children[1] instanceof ValueExpression) {
                cvalue = children[1].getConstantValue();
            } else {
                CompiledExpression compexpr = children[1].compile();
                cvalue = compexpr.getValue(null);
            }

            if (tableStream.addRelOpFilter(cexpr, relOp, cvalue))
                return null;
            else
                return this;
        } else {
            return this;
        }
    }

    @Override
    public void doBind() throws StreamSqlException {
        DataType ltype = children[0].getType();
        DataType rtype = children[1].getType();
        if (!compatibleTypes(ltype, rtype))
            throw new StreamSqlException(ErrCode.INCOMPATIBLE, ltype + " and " + rtype);
        type = DataType.BOOLEAN;
    }

    private boolean compatibleTypes(DataType ltype, DataType rtype) {
        // TODO Auto-generated method stub
        return true;
    }

    @Override
    public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {
        code.append("(");
        DataType ch0dt = children[0].getType();
        if (DataType.isNumber(ch0dt)) {
            children[0].fillCode_getValueReturn(code);
            code.append(relOp.getSign());
            children[1].fillCode_getValueReturn(code);
        } else {
            switch (relOp) {
            case NOT_EQUAL:
                code.append("!");
            case EQUAL: // intentional fall through
                children[0].fillCode_getValueReturn(code);
                if (DataType.isNumber(ch0dt)) {
                    code.append(" == ");
                    children[1].fillCode_getValueReturn(code);
                } else {
                    code.append(".equals(");
                    children[1].fillCode_getValueReturn(code);
                    code.append(")");
                }
                break;
            default:
                throw new StreamSqlException(ErrCode.COMPILE_ERROR, "Cannot use " + relOp + " not supported for data type " + ch0dt);
            }
        }
        code.append(")");
    }

    @Override
    public String toString() {
        return children[0] + " " + relOp.getSign() + " " + children[1];
    }
}
