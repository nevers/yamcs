package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.CompiledExpression;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.FieldReturnCompiledExpression;
import org.yamcs.yarch.ProtobufDataType;
import org.yamcs.yarch.streamsql.Expression;
import org.yamcs.yarch.streamsql.GenericStreamSqlException;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

import com.google.protobuf.Descriptors.Descriptor;

/**
 * Represents a column in a query, for example x and y below:
 * select x from table where y &gt; 0
 * 
 * @author nm
 *
 */
public class ColumnExpression extends Expression {
    String name;

    // after binding
    ColumnDefinition cdef;

    //for protobuf columns
    String fieldName;
    
    ColumnExpression(String name) throws ParseException {
        super(null);
        this.name = name;
        this.colName = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public void doBind() throws StreamSqlException {
        cdef = inputDef.getColumn(name);
        if (cdef == null) {
            int idx = name.indexOf(".");
            if(idx!=-1) { //protobuf column
                checkProtobuf(name.substring(0, idx), name.substring(idx+1));
            }       
        } else {
            type = cdef.getType();    
        }

        if(cdef==null) {
            throw new GenericStreamSqlException("'" + name + "' is not an input column");
        }
    }

    private void checkProtobuf (String className, String fieldName) throws GenericStreamSqlException {
        cdef = inputDef.getColumn(className);
        if(cdef==null) {
            throw new GenericStreamSqlException("'" + name + "' is not an input column");
        }

        DataType dt = cdef.getType();
        if(dt instanceof ProtobufDataType) {
            ProtobufDataType pdt = (ProtobufDataType) dt;
            Descriptor d = pdt.getDescriptor();
            if(d.findFieldByName(fieldName)==null) {
                throw new GenericStreamSqlException("'" + name + "' is not an input column");
            };
            this.fieldName = fieldName;
        } else {
            throw new GenericStreamSqlException("'" + name + "' is not an input column");
        }
    }

    @Override
    public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {
        if(fieldName==null) {
            code.append("col" + name);
        } else {
            code.append("col" + cdef.getName()+".get"+capitalizeFirstLetter(fieldName)+"()");
        }
    }

    @Override
    public CompiledExpression compile() throws StreamSqlException {
        return new FieldReturnCompiledExpression(name, cdef);
    }

    @Override
    public String toString() {
        return name;
    }
    
    
    private String capitalizeFirstLetter(String original) {
        if (original == null || original.length() == 0) {
            return original;
        }
        return original.substring(0, 1).toUpperCase() + original.substring(1);
    }
}
