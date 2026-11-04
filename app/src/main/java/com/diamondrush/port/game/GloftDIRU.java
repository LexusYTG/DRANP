package com.diamondrush.port.game;
import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;

public final class GloftDIRU extends MIDlet {
	// $FF: renamed from: a byte[]
	public static byte[] gameVersion = null;
	// $FF: renamed from: a i
	public cGame field_575;
	// $FF: renamed from: a javax.microedition.lcdui.Display
	public Display field_576;

	public GloftDIRU() {
		gameVersion = this.getAppProperty("MIDlet-Version").getBytes();
		this.field_575 = new cGame(this);
	}

	public final void startApp() throws MIDletStateChangeException {
		if (this.field_576 == null) {
			this.field_576 = Display.getDisplay(this);
		}

		this.field_576.setCurrent(this.field_575);
		if (this.field_575 != null) this.field_575.method_292();
	}

	public final void pauseApp() {
		if (this.field_575 != null) this.field_575.method_291();
		this.notifyPaused();
	}

	public final void destroyApp(boolean var1) throws MIDletStateChangeException {
		this.notifyDestroyed();
	}

	// $FF: renamed from: a () void
	public final void method_443() {
		cGame.method_289();
		if (this.field_575 != null && this.field_575.field_353 != null) this.field_575.field_353.method_55(); // Release sound player resources
		this.method_444();

		try {
			this.destroyApp(true);
		} catch (MIDletStateChangeException var2) {
		}
	}

	// $FF: renamed from: b () void
	private void method_444() {
		this.field_575 = null;
		this.field_576 = null;
		System.gc();
	}
}
