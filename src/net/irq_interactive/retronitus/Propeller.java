package net.irq_interactive.retronitus;

import java.io.InputStream;

public class Propeller {

	public Hub hub;

	public static class Hub {
		protected byte memory[];
		protected boolean shouldReset, lock[], lockused[];
		public Cog[] cogs;
		protected int cnt;

		protected Hub(int cognum) {
			memory = new byte[0x10000];
			lock = new boolean[8];
			lockused = new boolean[8];
			shouldReset = false;
			cogs = new Cog[cognum];
			for (int i = 0; i < cognum; i++) {
				cogs[i] = new Cog(false);
			}
		}

		public void reset() {
			shouldReset = true;
		}

		public int readLong(int addr) {
			addr &= 0xFFFC;
			return (((int) memory[addr + 0]) & 0xFF) | (((int) memory[addr + 1]) & 0xFF) << 8
					| (((int) memory[addr + 2]) & 0xFF) << 16 | (((int) memory[addr + 3]) & 0xFF) << 24;
		}

		public int readWord(int addr) {
			addr &= 0xFFFE;
			return (((int) memory[addr + 0]) & 0xFF) | (((int) memory[addr + 1]) & 0xFF) << 8;
		}

		public int readByte(int addr) {
			addr &= 0xFFFF;
			return ((int) memory[addr]) & 0xFF;
		}

		public void writeLong(int addr, int data) {
			if ((addr & 0x8000) != 0)
				return; // discard write to ROM
			addr &= 0xFFFC;
			memory[addr + 0] = (byte) data;
			data >>>= 8;
			memory[addr + 1] = (byte) data;
			data >>>= 8;
			memory[addr + 2] = (byte) data;
			data >>>= 8;
			memory[addr + 3] = (byte) data;
		}

		public void writeWord(int addr, int data) {
			if ((addr & 0x8000) != 0)
				return; // discard write to ROM
			addr &= 0xFFFE;
			memory[addr + 0] = (byte) data;
			data >>>= 8;
			memory[addr + 1] = (byte) data;
		}

		public void writeByte(int addr, int data) {
			if ((addr & 0x8000) != 0)
				return; // discard write to ROM
			addr &= 0xFFFF;
			memory[addr] = (byte) data;
		}

		public int coginit(int init,int master) {
			int cogid;
			int freecog = freecog();
			cogid = (init & 0x8) == 0 ? cogid = init & 0x7 : freecog;

			if (cogid != -1) {
				System.out.println("HUB: Starting cog"+cogid+" in cycle "+cnt+" from cog"+master);
				cogs[cogid].start(this, init, cogid);
			}

			return freecog; // ALWAYS RETURN FREE COG!!!!
		}

		public int cogstop(int id) {
			int freecog = freecog();
			System.out.println("HUB: Stopping cog"+(id&0x7)+" in cycle "+cnt);
			cogs[id & 0x7].stop();
			return freecog;
		}

		public int locknew() {
			int freelock = freelock();
			lockused[freelock] = true;
			return freelock;
		}

		public int freelock() {
			int freelock = -1;
			for (int i = 0; i < lockused.length; i++) {
				if (!lockused[i]) {
					freelock = i;
					break;
				}
			}
			return freelock;
		}

		public int lockret(int l) {
			int freelock = freelock();
			lockused[l & 0x7] = false;
			return freelock;
		}

		public boolean lockset(int l, boolean s) {
			l &= 0x7;
			boolean p = lock[l];
			lock[l] = s;
			return p;
		}

		public int freecog() {
			int freecog = -1;
			for (int i = 0; i < cogs.length; i++) {
				if (!cogs[i].isRunning()) {
					freecog = i;
					break;
				}
			}
			return freecog;
		}

		public void clock() {
			for (int i = 0; i < cogs.length; i++) {
				cogs[i].clock(cnt, i, this);
			}
			cnt++;
		}
	}

	public Propeller(int cognum, InputStream ram, InputStream rom) throws Exception {
		if (cognum > 8)
			throw new IllegalArgumentException("Only 8 cogs allowed");
		hub = new Hub(cognum);
		if (ram != null) {
			ram.read(hub.memory, 0, 0x8000);
		}
		rom.read(hub.memory, 0x8000, 0x8000);
		hub.cogs[0].start(hub, 0x007C010,0); // Start stuff
	}

	public void clock() {
		hub.clock();
	}

}
