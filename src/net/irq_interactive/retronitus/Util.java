package net.irq_interactive.retronitus;

import java.util.Scanner;

public class Util {

	protected static Scanner scan;

	public static int extend(boolean b) {
		return b ? 0xFFFFFFFF : 0;
	}
	
	public static String makeBinString(boolean b) {
		return b?"1":"0";
	}

	public static String zcri(boolean wz, boolean wc, boolean wr, boolean i) {
		return makeBinString(wz)+makeBinString(wc)+makeBinString(wr)+makeBinString(i);
	}

	public static String makeBinString(int i, int minbits) {
		return String.format("%" + minbits + "s", Integer.toBinaryString(i)).replace(' ', '0');
	}

	public static String makeHexString(int i, int minnibbles) {
		return String.format("%0" + minnibbles + "X", i);
	}
	
	public static String getDebug() {
		if (scan == null) {
			scan = new Scanner(System.in);
		}
		return scan.nextLine();
	}

}
