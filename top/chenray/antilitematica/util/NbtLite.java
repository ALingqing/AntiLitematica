package top.chenray.antilitematica.util;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class NbtLite {
   private static final int MAX_STRING_BYTES = 16384;
   private static final int MAX_LIST_LENGTH = 1000000;
   private static final int MAX_DEPTH = 32;

   private NbtLite() {
   }

   public static String tryReadRootCompoundString(byte[] data, int offset, String key) {
      if (data != null && key != null) {
         if (offset >= 0 && offset < data.length) {
            try {
               DataInputStream in = new DataInputStream(new ByteArrayInputStream(data, offset, data.length - offset));

               String var10;
               label53: {
                  label52: {
                     try {
                        int type = in.readUnsignedByte();
                        if (type != 0) {
                           if (type != 10) {
                              var10 = null;
                              break label52;
                           }

                           readUtf(in);
                           var10 = readCompoundFindString(in, key, 0);
                           break label53;
                        }

                        var10 = null;
                     } catch (Throwable var8) {
                        try {
                           in.close();
                        } catch (Throwable var6) {
                           var8.addSuppressed(var6);
                        }

                        throw var8;
                     }

                     in.close();
                     return var10;
                  }

                  in.close();
                  return var10;
               }

               in.close();
               return var10;
            } catch (IOException var9) {
               return null;
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static String readCompoundFindString(DataInputStream in, String key, int depth) throws IOException {
      if (depth > 32) {
         return null;
      } else {
         while(true) {
            int type;
            try {
               type = in.readUnsignedByte();
            } catch (EOFException var51) {
               return null;
            }

            if (type == 0) {
               return null;
            }

            String name = readUtf(in);
            if (type == 8 && key.equals(name)) {
               return readUtf(in);
            }

            skipPayload(in, type, depth);
         }
      }
   }

   private static void skipPayload(DataInputStream in, int type, int depth) throws IOException {
      switch (type) {
         case 1:
            in.skipBytes(1);
            break;
         case 2:
            in.skipBytes(2);
            break;
         case 3:
            in.skipBytes(4);
            break;
         case 4:
            in.skipBytes(8);
            break;
         case 5:
            in.skipBytes(4);
            break;
         case 6:
            in.skipBytes(8);
            break;
         case 7: {
            int arrLen = in.readInt();
            if (arrLen < 0) {
               throw new IOException("neg len");
            }
            in.skipBytes(arrLen);
            break;
         }
         case 8:
            readUtf(in);
            break;
         case 9: {
            int childType = in.readUnsignedByte();
            int listLen = in.readInt();
            if (listLen < 0 || listLen > 1000000) {
               throw new IOException("bad list len");
            }
            for(int i = 0; i < listLen; ++i) {
               skipListElement(in, childType, depth + 1);
            }
            break;
         }
         case 10:
            if (depth + 1 > 32) {
               throw new IOException("too deep");
            }
            while(true) {
               int t = in.readUnsignedByte();
               if (t == 0) {
                  return;
               }
               readUtf(in);
               skipPayload(in, t, depth + 1);
            }
         case 11: {
            int arrLen = in.readInt();
            if (arrLen < 0) {
               throw new IOException("neg len");
            }
            long arrBytes = (long)arrLen * 4L;
            if (arrBytes > 2147483647L) {
               throw new IOException("too big");
            }
            in.skipBytes((int)arrBytes);
            break;
         }
         case 12: {
            int arrLen = in.readInt();
            if (arrLen < 0) {
               throw new IOException("neg len");
            }
            long arrBytes = (long)arrLen * 8L;
            if (arrBytes > 2147483647L) {
               throw new IOException("too big");
            }
            in.skipBytes((int)arrBytes);
            break;
         }
         default:
            throw new IOException("unknown nbt type " + type);
      }

   }

   private static void skipListElement(DataInputStream in, int childType, int depth) throws IOException {
      switch (childType) {
         case 0:
            break;
         case 1:
            in.skipBytes(1);
            break;
         case 2:
            in.skipBytes(2);
            break;
         case 3:
            in.skipBytes(4);
            break;
         case 4:
            in.skipBytes(8);
            break;
         case 5:
            in.skipBytes(4);
            break;
         case 6:
            in.skipBytes(8);
            break;
         case 7: {
            int arrLen = in.readInt();
            if (arrLen < 0) {
               throw new IOException("neg len");
            }
            in.skipBytes(arrLen);
            break;
         }
         case 8:
            readUtf(in);
            break;
         case 9: {
            int nestedType = in.readUnsignedByte();
            int listLen = in.readInt();
            if (listLen < 0 || listLen > 1000000) {
               throw new IOException("bad list len");
            }
            for(int i = 0; i < listLen; ++i) {
               skipListElement(in, nestedType, depth + 1);
            }
            break;
         }
         case 10:
            if (depth > 32) {
               throw new IOException("too deep");
            }
            while(true) {
               int t = in.readUnsignedByte();
               if (t == 0) {
                  return;
               }
               readUtf(in);
               skipPayload(in, t, depth + 1);
            }
         case 11: {
            int arrLen = in.readInt();
            if (arrLen < 0) {
               throw new IOException("neg len");
            }
            long arrBytes = (long)arrLen * 4L;
            if (arrBytes > 2147483647L) {
               throw new IOException("too big");
            }
            in.skipBytes((int)arrBytes);
            break;
         }
         case 12: {
            int arrLen = in.readInt();
            if (arrLen < 0) {
               throw new IOException("neg len");
            }
            long arrBytes = (long)arrLen * 8L;
            if (arrBytes > 2147483647L) {
               throw new IOException("too big");
            }
            in.skipBytes((int)arrBytes);
            break;
         }
         default:
            throw new IOException("unknown list type " + childType);
      }

   }

   private static String readUtf(DataInputStream in) throws IOException {
      int len = in.readUnsignedShort();
      if (len >= 0 && len <= 16384) {
         byte[] b = new byte[len];
         in.readFully(b);
         return new String(b, StandardCharsets.UTF_8);
      } else {
         throw new IOException("bad str len");
      }
   }
}
