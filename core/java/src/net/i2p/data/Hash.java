package net.i2p.data;

/*
 * free (adj.): unencumbered; not under the control of others
 * Written by jrandom in 2003 and released into the public domain 
 * with no warranty of any kind, either expressed or implied.  
 * It probably won't make your computer catch on fire, or eat 
 * your children, but it might.  Use at your own risk.
 *
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import net.i2p.util.Log;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;

/**
 * Defines the hash as defined by the I2P data structure spec.
 * AA hash is the SHA-256 of some data, taking up 32 bytes.
 *
 * @author jrandom
 */
public class Hash extends DataStructureImpl {
    private final static Log _log = new Log(Hash.class);
    private byte[] _data;
    private volatile String _stringified;
    private volatile String _base64ed;
    private Map _xorCache;

    public final static int HASH_LENGTH = 32;
    public final static Hash FAKE_HASH = new Hash(new byte[HASH_LENGTH]);
    
    private static final int MAX_CACHED_XOR = 1024;
    
    public Hash() {
        setData(null);
    }

    public Hash(byte data[]) {
        setData(data);
    }

    public byte[] getData() {
        return _data;
    }

    public void setData(byte[] data) {
        _data = data;
        _stringified = null;
        _base64ed = null;
    }

    /**
     * Calculate the xor with the current object and the specified hash, 
     * caching values where possible.  Currently this keeps up to MAX_CACHED_XOR
     * (1024) entries, and uses an essentially random ejection policy.  Later 
     * perhaps go for an LRU or FIFO?
     *
     */
    public byte[] cachedXor(Hash key) {
        if (_xorCache == null) {
            // we dont want to create two of these
            synchronized (this) {
                if (_xorCache == null)
                    _xorCache = new HashMap(MAX_CACHED_XOR);
            }
        }

        // i think we can get away with this being outside the synchronized block
        byte[] distance = (byte[])_xorCache.get(key);
        
        if (distance == null) {
            // not cached, lets cache it
            int cached = 0;
            synchronized (_xorCache) {
                int toRemove = _xorCache.size() + 1 - MAX_CACHED_XOR;
                if (toRemove > 0) {
                    Set keys = new HashSet(toRemove);
                    // this removes essentially random keys - we dont maintain any sort
                    // of LRU or age.  perhaps we should?
                    for (Iterator iter = _xorCache.keySet().iterator(); iter.hasNext(); ) 
                        keys.add(iter.next());
                    for (Iterator iter = keys.iterator(); iter.hasNext(); ) 
                        _xorCache.remove(iter.next());
                }
                distance = DataHelper.xor(key.getData(), getData());
                _xorCache.put(key, (Object)distance);
                cached = _xorCache.size();
            }
            if (false && (_log.shouldLog(Log.DEBUG))) {
                // explicit buffer, since the compiler can't guess how long it'll be
                StringBuffer buf = new StringBuffer(128);
                buf.append("miss [").append(cached).append("] from ");
                buf.append(DataHelper.toHexString(getData())).append(" to ");
                buf.append(DataHelper.toHexString(key.getData()));
                _log.debug(buf.toString(), new Exception());
            }
        } else {
            if (false && (_log.shouldLog(Log.DEBUG))) {
                // explicit buffer, since the compiler can't guess how long it'll be
                StringBuffer buf = new StringBuffer(128);
                buf.append("hit from ");
                buf.append(DataHelper.toHexString(getData())).append(" to ");
                buf.append(DataHelper.toHexString(key.getData()));
                _log.debug(buf.toString());
            }
        }
        return distance;
    }
    
    public void clearXorCache() {
        _xorCache = null;
    }
    
    public void readBytes(InputStream in) throws DataFormatException, IOException {
        _data = new byte[HASH_LENGTH];
        _stringified = null;
        _base64ed = null;
        int read = read(in, _data);
        if (read != HASH_LENGTH) throw new DataFormatException("Not enough bytes to read the hash");
    }

    public void writeBytes(OutputStream out) throws DataFormatException, IOException {
        if (_data == null) throw new DataFormatException("No data in the hash to write out");
        if (_data.length != HASH_LENGTH) throw new DataFormatException("Invalid size of data in the private key");
        out.write(_data);
    }

    public boolean equals(Object obj) {
        if ((obj == null) || !(obj instanceof Hash)) return false;
        return DataHelper.eq(_data, ((Hash) obj)._data);
    }

    public int hashCode() {
        return DataHelper.hashCode(_data);
    }

    public String toString() {
        if (_stringified == null) {
            StringBuffer buf = new StringBuffer(64);
            buf.append("[Hash: ");
            if (_data == null) {
                buf.append("null hash");
            } else {
                buf.append(toBase64());
            }
            buf.append("]");
            _stringified = buf.toString();
        }
        return _stringified;
    }
    
    public String toBase64() {
        if (_base64ed == null) {
            _base64ed = super.toBase64();
        }
        return _base64ed;
    }
    
    public static void main(String args[]) {
        testFill();
        testOverflow();
        testFillCheck();
    }
    
    private static void testFill() {
        Hash local = new Hash(new byte[HASH_LENGTH]); // all zeroes
        for (int i = 0; i < MAX_CACHED_XOR; i++) {
            byte t[] = new byte[HASH_LENGTH];
            for (int j = 0; j < HASH_LENGTH; j++)
                t[j] = (byte)((i >> j) & 0xFF);
            Hash cur = new Hash(t);
            local.cachedXor(cur);
            if (local._xorCache.size() != i+1) {
                _log.error("xor cache size where i=" + i + " isn't correct!  size = " 
                           + local._xorCache.size());
                return;
            }
        }
    }
    private static void testOverflow() {
        Hash local = new Hash(new byte[HASH_LENGTH]); // all zeroes
        for (int i = 0; i < MAX_CACHED_XOR*2; i++) {
            byte t[] = new byte[HASH_LENGTH];
            for (int j = 0; j < HASH_LENGTH; j++)
                t[j] = (byte)((i >> j) & 0xFF);
            Hash cur = new Hash(t);
            local.cachedXor(cur);
            if (i < MAX_CACHED_XOR) {
                if (local._xorCache.size() != i+1) {
                    _log.error("xor cache size where i=" + i + " isn't correct!  size = " 
                               + local._xorCache.size());
                    return;
                }
            } else {
                if (local._xorCache.size() > MAX_CACHED_XOR) {
                    _log.error("xor cache size where i=" + i + " isn't correct!  size = " 
                               + local._xorCache.size());
                    return;
                }
            }
        }
    }
    private static void testFillCheck() {
        Set hashes = new HashSet();
        Hash local = new Hash(new byte[HASH_LENGTH]); // all zeroes
        // fill 'er up
        for (int i = 0; i < MAX_CACHED_XOR; i++) {
            byte t[] = new byte[HASH_LENGTH];
            for (int j = 0; j < HASH_LENGTH; j++)
                t[j] = (byte)((i >> j) & 0xFF);
            Hash cur = new Hash(t);
            hashes.add(cur);
            local.cachedXor(cur);
            if (local._xorCache.size() != i+1) {
                _log.error("xor cache size where i=" + i + " isn't correct!  size = " 
                           + local._xorCache.size());
                return;
            }
        }
        // now lets recheck using those same hash objects 
        // and see if they're cached
        for (Iterator iter = hashes.iterator(); iter.hasNext(); ) {
            Hash cur = (Hash)iter.next();
            if (!local._xorCache.containsKey(cur)) {
                _log.error("checking the cache, we dont have " 
                           + DataHelper.toHexString(cur.getData()));
                return;
            }
        }
        // now lets recheck with new objects but the same values 
        // and see if they'return cached
        for (int i = 0; i < MAX_CACHED_XOR; i++) {
            byte t[] = new byte[HASH_LENGTH];
            for (int j = 0; j < HASH_LENGTH; j++)
                t[j] = (byte)((i >> j) & 0xFF);
            Hash cur = new Hash(t);
            if (!local._xorCache.containsKey(cur)) {
                _log.error("checking the cache, we do NOT have " 
                           + DataHelper.toHexString(cur.getData()));
                return;
            }
        }
    }
}