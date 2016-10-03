package net.irq_interactive.retronitus;

import java.util.Arrays;

import net.irq_interactive.retronitus.Propeller.Hub;

public class Cog {

	public boolean singlestep;
	protected int ram[];
	protected State state;
	protected int pc;
	protected boolean z, c;

	protected int execUntil, spinUntil;
	protected boolean isSpin;

	public enum State {
		STOP, RUN
	}

	public static final int spinPC = 0x1EE;
	public static final int spinPRG = 0xF004;

	public Cog(boolean single) {
		ram = new int[512];
		state = State.STOP;
		singlestep = single;
	}

	public void start(Hub hub, int init, int cogid) {
		Arrays.fill(ram, 0);
		int par = (init >>> 16) & 0xFFFC;
		int prg = (init >>> 2) & 0xFFFC;
		for (int i = 0; i < 0x1F0; i++) {
			ram[i] = hub.readLong(prg + (i * 4));
		}
		ram[0x1F0] = par;
		pc = 0;
		c = false;
		z = false;
		state = State.RUN;

		System.out.println("DEBUG: cog" + cogid + " started @" + Util.makeHexString(prg, 4) + " \t PAR: "
				+ Util.makeHexString(par, 4));

		isSpin = prg == spinPRG;
		execUntil = -1;
		spinUntil = -1;
	}

	public void stop() {
		state = State.STOP;
	}

	@SuppressWarnings("unused")
	public void clock(int cnt, int cogid, Hub hub) {
		if (state == State.STOP)
			return;
		ram[0x1F1] = cnt;

		final int opcode = ram[pc];
		final int instr = opcode >>> 26;
		final boolean wz = (opcode & (1 << 25)) != 0;
		final boolean wc = (opcode & (1 << 24)) != 0;
		final boolean wr = (opcode & (1 << 23)) != 0;
		final boolean literal = (opcode & (1 << 22)) != 0;
		final int con = (opcode >>> 18) & 0xF;
		final int dest = (opcode >>> 9) & 0x1FF;
		final int src = opcode & 0x1FF;

		final int source = literal ? src : ram[src];
		final int destination = ram[dest];
		int result = ram[dest]; // gets written back later
		boolean newc = c, newz = z;

		int pcprev = pc;

		if (condition(con)) {
			switch (instr) {
			case 0b000000: { // WRBYTE,RDBYTE
				if (wr) {// read/write depends on wr flag; wr == true means:
							// read
							// from hub, write to cog
					result = hub.readByte(source);
					newz = result == 0;
				} else {
					hub.writeByte(source, result);
				}
				pc++;
				break;
			}
			case 0b000001: { // WRWORD,RDWORD
				if (wr) {// read/write depends on wr flag; wr == true means:
							// read
							// from hub, write to cog
					result = hub.readWord(source);
					newz = result == 0;
				} else {
					hub.writeWord(source, result);
				}
				pc++;
				break;
			}
			case 0b000010: { // WRLONG,RDLONG
				if (wr) {// read/write depends on wr flag; wr == true means:
							// read
							// from hub, write to cog
					result = hub.readLong(source);
					newz = result == 0;
				} else {
					hub.writeLong(source, result);
				}
				pc++;
				break;
			}
			case 0b000011: { // HUBOP and stuff
				switch (source & 0x7) {
				default:
				case 0b000: // CLKSET Clock is not emulated, ignore, except
							// reset
							// flag.
					if ((result & 0x80) != 0)
						hub.reset(); // schedule reset
					break;
				case 0b001: // COGID
					result = cogid;
					newz = cogid == 0;
					newc = false;
					break;
				case 0b010: // COGINIT
					int coginit = hub.coginit(result, cogid);
					result = coginit; // TODO: This is probably inaccurate. WTF
										// even
										// happens when starting a new cog with
										// all
										// 8 already running?
					newc = coginit == -1;
					newz = coginit == 0;
					break;
				case 0b011: // COGSTOP
					int cogstop = hub.cogstop(result);
					newc = cogstop == -1;
					newz = (result & 0x7) == 0;
					break;
				case 0b100: // LOCKNEW
					result = hub.locknew(); // TODO: Inaccurate
					newc = result == -1;
					newz = result == 0;
					break;
				case 0b101: // LOCKRET
					result &= 0x7;
					int lockret = hub.lockret(result); // TODO: Inaccurate
					newc = lockret == -1;
					newz = result == 0;
					break;
				case 0b110: // LOCKSET
					result &= 0x7;
					newz = result == 0;
					newc = hub.lockset(result, true); // TODO: Inaccurate
					break;
				case 0b111: // LOCKCLR
					result &= 0x7;
					newz = result == 0;
					newc = hub.lockset(result, false); // TODO: Inaccurate
					break;
				}
				pc++;
				break;
			}

			/*
			 * Reserved instructions would go here!
			 */

			case 0b001000: { // ROR
				result = (result >>> source) | (result << (32 - source));
				newz = result == 0;
				newc = (result & 0x1) != 0;
				pc++;
				break;
			}
			case 0b001001: { // ROL
				result = (result << source) | (result >>> (32 - source));
				newz = result == 0;
				newc = (result & (1 << 31)) != 0;
				pc++;
				break;
			}
			case 0b001010: { // SHR
				result = result >>> source;
				newz = result == 0;
				newc = (result & 0x1) != 0;
				pc++;
				break;
			}
			case 0b001011: { // SHL
				result = result << source;
				newz = result == 0;
				newc = (result & (1 << 31)) != 0;
				pc++;
				break;
			}
			case 0b001100: { // RCR
				result = result >>> source;
				int mask = -1 >>> source;
				if (c)
					result |= ~mask;
				newz = result == 0;
				newc = (result & 0x1) != 0;
				pc++;
				break;
			}
			case 0b001101: { // RCL
				result = result << source;
				int mask = -1 << source;
				newz = result == 0;
				if (c)
					result |= ~mask;
				newc = (result & (1 << 31)) != 0;
				pc++;
				break;
			}
			case 0b001110: { // SAR
				result = result >> source;
				newz = result == 0;
				newc = (result & 0x1) != 0;
				pc++;
				break;
			}
			case 0b001111: { // REV
				result = Integer.reverse(result) >> source;
				newz = result == 0;
				newc = (result & 0x1) != 0;
				pc++;
				break;
			}
			case 0b010000: { // MINS
				boolean mins = result < source;
				result = mins ? source : result;
				newz = source == 0;
				newc = mins;
				pc++;
				break;
			}
			case 0b010001: { // MAXS
				newc = result < source;
				result = result >= source ? source : result;
				newz = source == 0;
				pc++;
				break;
			}
			case 0b010010: { // MIN
				newc = Integer.compareUnsigned(result, source) < 0;
				result = newc ? source : result;
				newz = source == 0;
				pc++;
				break;
			}
			case 0b010011: { // MAX
				int max = Integer.compareUnsigned(result, source);
				newc = max < 0;
				result = max >= 0 ? source : result;
				newz = source == 0;
				pc++;
				break;
			}
			case 0b010100: { // MOVS
				result = (result & ~0x1FF) | (source & 0x1FF);
				newz = source == 0;
				pc++;
				break;
			}
			case 0b010101: { // MOVD
				result = (result & ~0x3FE00) | ((source & 0x1FF) << 9);
				newz = source == 0;
				pc++;
				break;
			}
			case 0b010110: { // MOVI
				result = (result & ~0xFF800000) | ((source & 0x1FF) << 23);
				newz = source == 0;
				pc++;
				break;
			}
			case 0b010111: { // JMPRET,JMP,CALL,RET
				result = (result & ~0x1FF) | (pc + 1);
				newz = result == 0;
				// newc = (pc + 1) == 0; //WTF was I thinking?
				pc = source & 0x1FF;
				break;
			}
			case 0b011000: { // TEST,AND
				result = result & source;
				newz = result == 0;
				newc = (Integer.bitCount(result) & 1) == 1;
				pc++;
				break;
			}
			case 0b011001: { // TESTN,ANDN
				result = result & ~source;
				newz = result == 0;
				newc = (Integer.bitCount(result) & 1) == 1;
				pc++;
				break;
			}
			case 0b011010: { // OR
				result = result | source;
				newz = result == 0;
				newc = (Integer.bitCount(result) & 1) == 1;
				pc++;
				break;
			}
			case 0b011011: { // XOR
				result = result ^ source;
				newz = result == 0;
				newc = (Integer.bitCount(result) & 1) == 1;
				pc++;
				break;
			}
			case 0b011100: { // MUXC
				result = (result & ~source) | (source & Util.extend(c));
				newz = result == 0;
				newc = (Integer.bitCount(result) & 1) == 1;
				pc++;
				break;
			}
			case 0b011101: { // MUXNC
				result = (result & ~source) | (source & Util.extend(!c));
				newz = result == 0;
				newc = (Integer.bitCount(result) & 1) == 1;
				pc++;
				break;
			}
			case 0b011110: { // MUXZ
				result = (result & ~source) | (source & Util.extend(z));
				newz = result == 0;
				newc = (Integer.bitCount(result) & 1) == 1;
				pc++;
				break;
			}
			case 0b011111: { // MUXNZ
				result = (result & ~source) | (source & Util.extend(!z));
				newz = result == 0;
				newc = (Integer.bitCount(result) & 1) == 1;
				pc++;
				break;
			}
			case 0b100000: { // ADD
				long add = (((long) result) & -1) + (((long) source) & -1);
				result = (int) (add & -1);
				newz = result == 0;
				newc = (add & 0x100000000l) != 0;
				pc++;
				break;
			}
			case 0b100001: { // SUB,CMP
				long sub = (((long) result) & -1) - (((long) source) & -1);
				result = (int) (sub & -1);
				newz = result == 0;
				newc = (sub & 0x100000000l) != 0;
				pc++;
				break;
			}
			case 0b100010: { // ADDABS
				long add = (((long) result) & -1) + (((long) Math.abs(source)) & -1);
				result = (int) (add & -1);
				newz = result == 0;
				newc = (add & 0x100000000l) != 0;
				pc++;
				break;
			}
			case 0b100011: { // SUBABS
				long sub = (((long) result) & -1) - (((long) Math.abs(source)) & -1);
				result = (int) (sub & -1);
				newz = result == 0;
				newc = (sub & 0x100000000l) != 0;
				pc++;
				break;
			}
			case 0b100100: { // SUMC
				int s = (c ? -source : source);
				long add = result + s;
				int dold = result;
				result = (int) (add & -1);
				newc = ((s & dold & ~result) | (~s & ~dold & result)) < 0;
				newz = result == 0; // GEAR source indicates that the zero flag
									// is ANDed with it's previous value like in
									// ADDSX and such. The Prop manual says it
									// is always set when the the result is
									// zero. dafuq?
				pc++;
				break;
			}
			case 0b100101: { // SUMNC
				int s = (!c ? -source : source);
				long add = (((long) result) & -1) + (((long) s) & -1);
				int dold = result;
				result = (int) (add & -1);
				newc = ((s & dold & ~result) | (~s & ~dold & result)) < 0;
				newz = result == 0;
				pc++;
				break;
			}
			case 0b100110: { // SUMZ
				int s = (z ? -source : source);
				long add = (((long) result) & -1) + (((long) s) & -1);
				int dold = result;
				result = (int) (add & -1);
				newc = ((s & dold & ~result) | (~s & ~dold & result)) < 0;
				newz = result == 0;
				pc++;
				break;
			}
			case 0b100111: { // SUMNZ
				int s = (!z ? -source : source);
				long add = (((long) result) & -1) + (((long) s) & -1);
				int dold = result;
				result = (int) (add & -1);
				newc = ((s & dold & ~result) | (~s & ~dold & result)) < 0;
				newz = result == 0;
				pc++;
				break;
			}
			case 0b101000: { // MOV
				result = source;
				newz = result == 0;
				newc = (source & (1 << 31)) != 0;
				pc++;
				break;
			}
			case 0b101001: { // NEG
				result = -source;
				newz = result == 0;
				newc = (source & (1 << 31)) != 0;
				pc++;
				break;
			}
			case 0b101010: { // ABS
				result = Math.abs(source);
				newz = result == 0;
				newc = (source & (1 << 31)) != 0;
				pc++;
				break;
			}
			case 0b101011: { // ABSNEG
				result = -Math.abs(source);
				newz = result == 0;
				newc = (source & (1 << 31)) != 0;
				pc++;
				break;
			}
			case 0b101100: { // NEGC
				result = c ? -source : source;
				newz = result == 0;
				newc = (source & (1 << 31)) != 0;
				pc++;
				break;
			}
			case 0b101101: { // NEGNC
				result = !c ? -source : source;
				newz = result == 0;
				newc = (source & (1 << 31)) != 0;
				pc++;
				break;
			}
			case 0b101110: { // NEGZ
				result = z ? -source : source;
				newz = result == 0;
				newc = (source & (1 << 31)) != 0;
				pc++;
				break;
			}
			case 0b101111: { // NEGNZ
				result = !z ? -source : source;
				newz = result == 0;
				newc = (source & (1 << 31)) != 0;
				pc++;
				break;
			}
			case 0b110000: { // CMPS
				// This instruction is strange
				newz = result == source;
				newc = result < source;
				result = result - source;
				pc++;
				break;
			}
			case 0b110001: { // CMPSX
				result = result - (source + (c ? 1 : 0));
				newz = result == 0 && z;
				newc = result < source + (c ? 1 : 0);
				pc++;
				break;
			}
			case 0b110010: { // ADDX
				long add = (((long) result) & -1) + (((long) source) & -1) + (c ? 1 : 0);
				result = (int) (add & -1);
				newz = result == 0 && z;
				newc = (add & 0x100000000l) != 0;
				pc++;
				break;
			}
			case 0b110011: { // SUBX
				long sub = (((long) result) & -1) - (((long) (source + (c ? 1 : 0))) & -1);
				result = (int) (sub & -1);
				newz = result == 0 && z;
				newc = (sub & 0x100000000l) != 0;
				pc++;
				break;
			}

			case 0b110100: { // ADDS
				long add = result + source;
				int dold = result;
				result = (int) (add & -1);
				newc = ((source ^ dold)&0x80000000) == 0 && ((source ^ result)&0x80000000) != 0;
				newz = result == 0;
				pc++;
				break;
			}
			case 0b110101: { // SUBS
				long sub = result - source;
				int dold = result;
				result = (int) (sub & -1);
				newz = result == 0;
				newc = ((~source & dold & ~result) | (source & ~dold & result)) < 0;
				pc++;
				break;
			}
			case 0b110110: { // ADDSX
				long add = source + result + (c ? 1 : 0);
				int dold = result;
				result = (int) (add & -1);
				newc = ((source ^ dold)&0x80000000) == 0 && ((source ^ result)&0x80000000) != 0;
				newz = result == 0 && z;
				pc++;
				break;
			}
			case 0b110111: { // SUBSX
				int s = source + (c ? 1 : 0);
				long sub = result - (s);
				int dold = result;
				result = (int) (sub & -1);
				newz = result == 0 && z;
				newc = ((source ^ dold)&0x80000000) != 0 && ((source ^ result)&0x80000000) != 0;
				pc++;
				break;
			}
			case 0b111000: { // CMPSUB
				boolean t = Integer.compareUnsigned(result, source) >= 0;
				newz = result == source;
				newc = t;
				if (t) {
					long sub = (((long) result) & -1) - (((long) source) & -1);
					result = (int) (sub & -1);
				}
				pc++;
				break;
			}
			case 0b111001: { // DJNZ
				long sub = (((long) result) & -1) - 1;
				result = (int) (sub & -1);
				newz = result == 0;
				newc = (sub & 0x100000000l) != 0;
				pc = newz ? pc + 1 : source;
				break;
			}
			case 0b111010: { // TJNZ
				int not_result = result & source;
				newz = result == 0;
				newc = false;
				pc = not_result == 0 ? pc + 1 : source;
				break;
			}
			case 0b111011: { // TJZ
				int not_result = result & source;
				newz = result == 0;
				newc = false;
				pc = not_result != 0 ? pc + 1 : source;
				break;
			}
			case 0b111100: { // WAITPEQ
				// TODO: not emulated
				pc++;
				break;
			}
			case 0b111101: { // WAITPNE
				// TODO: not emulated
				pc++;
				break;
			}
			case 0b111110: { // WAITCNT
				// not emulated. Would be easy to implement, but the performance
				// would suffer.
				pc++;
				break;
			}
			case 0b111111: { // WAITVID
				// Used as DEBUG instruction
				System.out.println("DEBUG: Waitvid in cog" + cogid + " @" + Util.makeHexString(pc, 3) + "!\nS: \t"
						+ Util.makeHexString(source, 8) + " \tD: " + Util.makeHexString(result, 8));
				pc++;
				break;
			}

			default: // meh
				System.out.println("Invalid opcode " + Util.makeHexString(opcode, 8) + "@" + Util.makeHexString(pc, 3)
						+ " in cog" + cogid + " !!!");
				singlestep = true;
				pc++;
				break;

			}

			debug(false, cogid, result, src, newc, newz, pcprev, instr, con, dest, opcode, source, destination, wc, wz,
					wr, literal, hub, cnt);

			if (wr)
				ram[dest] = result;
			if (wc)
				c = newc;
			if (wz)
				z = newz;

			if (true && wr && dest == 0x1FB) { // Write to FRQB
				Audio.pushSample(ram[0x1FA], result);
			}

		} else {
			pc++;
			debug(true, cogid, result, src, newc, newz, pcprev, instr, con, dest, opcode, source, destination, wc, wz,
					wr, literal, hub, cnt);
		}
		if (pc > 511) {
			System.err.println("PC overflow in cog" + cogid);
			// pc &= 0x1FF;
		}
		if (false)
			;

	}

	private boolean condition(int con) {
		switch (con) {
		default:
		case 0b0000:
			return false;
		case 0b0001:
			return !z && !c;
		case 0b0010:
			return z && !c;
		case 0b0011:
			return !c;
		case 0b0100:
			return !z && c;
		case 0b0101:
			return !z;
		case 0b0110:
			return z != c;
		case 0b0111:
			return !z || !c;
		case 0b1000:
			return z && c;
		case 0b1001:
			return z == c;
		case 0b1010:
			return z;
		case 0b1011:
			return z || !c;
		case 0b1100:
			return c;
		case 0b1101:
			return !z || c;
		case 0b1110:
			return z || c;
		case 0b1111:
			return true;
		}
	}

	public void debug(boolean skip, int cogid, int result, int src, boolean newc, boolean newz, int pcprev, int instr,
			int con, int dest, int opcode, int source, int destination, boolean wc, boolean wz, boolean wr,
			boolean literal, Hub hub, int cnt) {
		if (singlestep) {
			execUntil = -1;
			spinUntil = -1;
		} else {
			if (execUntil == pc || spinUntil == (ram[spinPC] & 0xFFFF)) {
				// high word of pcurr is used for strangeness
				singlestep = true;
				execUntil = -1;
				spinUntil = -1;
			}
		}
		if (singlestep) {
			System.out.println("DEBUG: cog" + cogid + (skip ? " skipped" : " executed") + " opcode "
					+ Util.makeHexString(opcode, 8) + "@" + Util.makeHexString(pcprev, 3)
					+ "\nINSTR \tZCRI \tCON \tDEST \tSRC\n" + Util.makeBinString(instr, 6) + "\t"
					+ Util.zcri(wz, wc, wr, literal) + "\t" + Util.makeBinString(con, 4) + "\t"
					+ Util.makeHexString(dest, 3) + "\t" + Util.makeHexString(src, 3) + "\nS: \t"
					+ Util.makeHexString(source, 8) + " \tD: " + Util.makeHexString(destination, 8) + "\nRESULT: \t"
					+ Util.makeHexString(result, 8) + "\nC:\tOLD: " + c + " \tNEW(ifWC): " + newc + "\nZ:\tOLD: " + z
					+ " \tNEW(ifWZ): " + newz + "\nPC:\tOLD: " + Util.makeHexString(pcprev, 3) + " \tNEW:       "
					+ Util.makeHexString(pc, 3)
					+ (isSpin ? "\nSPIN:\npcurr: \t" + Util.makeHexString(ram[spinPC], 4) : ""));

			next: while (true) {
				String in = Util.getDebug();
				if (in.isEmpty()) {
					break next;
				} else {
					switch (in.charAt(0)) {
					case 'r': {
						singlestep = false;
						break next;
					}
					case 'u': {
						singlestep = false;
						execUntil = Integer.parseInt(in.substring(1).trim(), 16) & 0x1FF;
						spinUntil = -1;
						break next;
					}
					case 's': {
						singlestep = false;
						spinUntil = Integer.parseInt(in.substring(1).trim(), 16) & 0xFFFF;
						execUntil = -1;
						break next;
					}
					case 'c': {
						int i = Integer.parseInt(in.substring(1).trim(), 16) & 0x1FF;
						System.out.println("COG@" + Util.makeHexString(i, 3) + " : \t" + Util.makeHexString(ram[i], 8));
						break;
					}
					case 'h': {
						int i = Integer.parseInt(in.substring(1).trim(), 16) & 0xFFFC;
						System.out.println(
								"HUB@" + Util.makeHexString(i, 4) + " : \t" + Util.makeHexString(hub.readLong(i), 8));
						break;
					}
					default: {
						System.err.println("Syntax Error!");
						break;
					}
					}
				}

			}
		}
	}

	public boolean isRunning() {
		return state == State.RUN;
	}

}