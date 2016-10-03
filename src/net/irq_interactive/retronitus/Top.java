package net.irq_interactive.retronitus;

import java.io.BufferedInputStream;

public class Top {

	public static void main(String[] args) throws Exception {
		Propeller prop = new Propeller(2,
				new BufferedInputStream(Top.class.getResourceAsStream("81720.binary")),
				new BufferedInputStream(Top.class.getResourceAsStream("unscramble.rom")));
		while (true) {
			prop.clock();
		}
	}

}
