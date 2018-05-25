package org.yamcs.yarch;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectProperties;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectPropertiesOrBuilder;

public interface Bucket {
    /**
     * get the bucket name
     * @return
     */
    String getName();
    /**
     * retrieve objects that match the condition
     * 
     * @param p - predicate to be matched by the returned objects
     * @return list of objects
     * @throws IOException
     */
    List<ObjectProperties> listObjects(Predicate<ObjectPropertiesOrBuilder> p) throws IOException;
    void uploadObject(String objectName, String contentType, Map<String, String> metadata, byte[] objectData) throws IOException;
    byte[] getObject(String objectName)  throws IOException;
    void deleteObject(String objectName)  throws IOException;
    
    /**
     * retrieve the object properties or null if not such an object exist
     * @param objectName
     * @return
     * @throws IOException 
     * @throws  
     */
    ObjectProperties findObject(String objectName) throws IOException;
        
}
