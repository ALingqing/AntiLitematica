package top.chenray.antilitematica.util;

public final class MinecraftVarInt {
   private MinecraftVarInt() {
   }

   public static ReadResult read(byte[] buf, int start) {
      if (buf == null) {
         return new ReadResult(false, 0, start);
      } else {
         int numRead = 0;
         int result = 0;
         int index = start;

         while(index < buf.length) {
            int read = buf[index++] & 255;
            int value = read & 127;
            result |= value << 7 * numRead;
            ++numRead;
            if (numRead > 5) {
               return new ReadResult(false, 0, start);
            }

            if ((read & 128) == 0) {
               return new ReadResult(true, result, index);
            }
         }

         return new ReadResult(false, 0, start);
      }
   }

   public static record ReadResult(boolean ok, int value, int nextIndex) {
   }
}
