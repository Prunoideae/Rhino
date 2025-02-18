package dev.latvian.mods.rhino.util.unit;

public class XorUnit extends OpUnit {
	public XorUnit(Unit u, Unit w) {
		super(u, w);
	}

	@Override
	public String getSymbols() {
		return "^";
	}

	@Override
	public float get() {
		return unit.getAsInt() ^ with.getAsInt();
	}

	@Override
	public int getAsInt() {
		return unit.getAsInt() ^ with.getAsInt();
	}

	@Override
	public boolean getAsBoolean() {
		return unit.getAsBoolean() != with.getAsBoolean();
	}
}
