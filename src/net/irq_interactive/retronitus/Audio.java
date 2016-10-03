package net.irq_interactive.retronitus;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line.Info;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

public class Audio {

	protected static SourceDataLine line;
	protected static Mixer mixer;
	public static final float samplerate = 78000 / 4;
	public static String fileOut = null;
	protected static OutputStream f;
	public static final boolean signed = false;

	public static void pushSample(int l, int r) {
		
		
		if (signed) {
			int lo = l,ro = r;
			l^=Integer.MIN_VALUE;
			r^=Integer.MIN_VALUE;
		}
		
		 //byte[] b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putShort((short)(l>>>16)).putShort((short)(r>>>16)).array();
		//byte[] b = new byte[] { (byte) (l >>> 16), (byte) (l >>> 24), (byte) (r >>> 16), (byte) (r >>> 24) };
		byte[] b = new byte[] {(byte) (l >>> 24), (byte) (r >>> 24) };
		// byte[] b = new byte[] {127,127,127,127};
		if (fileOut == null) {
			if (line == null) {
				AudioFormat format = new AudioFormat(samplerate, 8, 2, signed, false);
				try {
					mixer = AudioSystem.getMixer(null);
					mixer.open();
					line = (SourceDataLine) mixer.getLine(new Info(SourceDataLine.class));
					line.open(format);
					line.start();
				} catch (LineUnavailableException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			// System.out.println(l+""+r);
			//
			// line.write(b,0, 4);
			line.write(b, 0, 2);
			// line.write(b,0,4);
		} else {
			try {
				if (f == null) {
					f = new FileOutputStream(fileOut);
				}
				f.write(b);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

}
