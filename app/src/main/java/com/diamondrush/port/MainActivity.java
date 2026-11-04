package com.diamondrush.port;

import android.app.Activity;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import com.diamondrush.port.game.GloftDIRU;
import com.diamondrush.port.game.Res;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.media.Manager;
import javax.microedition.midlet.MIDlet;

public class MainActivity extends Activity {
    private static final String TAG = "DiamondRush";
    private static final long DOUBLE_BACK_MS = 400;

    // cGame tiene mucho estado static: el midlet es uno solo por proceso.
    private static GloftDIRU midlet;
    private static boolean paused = false;
    private static boolean exiting = false;

    private GameView gameView;
    private long lastBackAt = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Res.init(this);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        applyImmersive();

        MIDlet.setLifecycleListener(new MIDlet.LifecycleListener() {
            @Override public void onDestroyed() {
                exiting = true;
                runOnUiThread(new Runnable() {
                    @Override public void run() { finish(); }
                });
            }
            @Override public void onPaused() {}
            @Override public void onResumeRequested() {}
        });

        // setListener re-notifica si ya hay un Displayable actual (Activity recreada)
        Display.setListener(new Display.Listener() {
            @Override public void onSetCurrent(final Displayable d) {
                runOnUiThread(new Runnable() {
                    @Override public void run() { showDisplayable(d); }
                });
            }
        });

        if (midlet == null) {
            try {
                midlet = new GloftDIRU();
                paused = false;
                midlet.startApp();
            } catch (Throwable t) {
                Log.e(TAG, "startApp failed", t);
            }
        }
    }

    private void showDisplayable(Displayable d) {
        if (!(d instanceof Canvas)) return;
        if (gameView != null && gameView.getCanvas() == d) return;
        gameView = new GameView(this, (Canvas) d);
        setContentView(gameView);
        gameView.requestFocus();
    }

    @Override protected void onResume() {
        super.onResume();
        applyImmersive();
        Manager.resumeAll();
        if (midlet != null && paused && !exiting) {
            paused = false;
            try {
                midlet.startApp();
            } catch (Throwable t) {
                Log.e(TAG, "startApp (resume) failed", t);
            }
        }
    }

    @Override protected void onPause() {
        if (gameView != null) gameView.releaseAll();
        Manager.pauseAll();
        if (midlet != null && !paused && !exiting) {
            paused = true;
            try {
                midlet.pauseApp();
            } catch (Throwable t) {
                Log.e(TAG, "pauseApp failed", t);
            }
        }
        super.onPause();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            // el estado static de cGame no se puede reiniciar: se mata el proceso
            android.os.Process.killProcess(android.os.Process.myPid());
        } else {
            Display.setListener(null);
            MIDlet.setLifecycleListener(null);
            gameView = null;
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive();
        else if (gameView != null) gameView.releaseAll();
    }

    @SuppressWarnings("deprecation")
    private void applyImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    // ------------------------------------------------------------------ salida

    private void exitGame() {
        exiting = true;
        if (midlet != null) {
            try {
                midlet.destroyApp(false);
            } catch (Throwable t) {
                Log.e(TAG, "destroyApp failed", t);
            }
        }
        finish();
    }

    /** Back = RSH (-7). Dos Back en menos de 400 ms cierran el juego. */
    private boolean handleBackDown() {
        long now = SystemClock.uptimeMillis();
        if (now - lastBackAt < DOUBLE_BACK_MS) {
            exitGame();
            return true;
        }
        lastBackAt = now;
        if (gameView != null) gameView.pressKey(GameView.K_RSH);
        return true;
    }

    @Override public void onBackPressed() {
        // solo llega por el camino de back predictivo; el camino por KeyEvent lo consume onKeyDown
        if (handleBackDown() && gameView != null) gameView.releaseKey(GameView.K_RSH);
    }

    // ------------------------------------------------------------------ teclado / gamepad

    private static int mapKey(int kc) {
        switch (kc) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_W:
                return GameView.K_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_S:
                return GameView.K_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_A:
                return GameView.K_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_D:
                return GameView.K_RIGHT;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                return GameView.K_FIRE;
            case KeyEvent.KEYCODE_BUTTON_L1:
                return GameView.K_LSH;
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_BUTTON_B:
                return GameView.K_RSH;
            case KeyEvent.KEYCODE_STAR:
                return Canvas.KEY_STAR;
            case KeyEvent.KEYCODE_POUND:
                return Canvas.KEY_POUND;
            default:
                break;
        }
        if (kc >= KeyEvent.KEYCODE_0 && kc <= KeyEvent.KEYCODE_9) return Canvas.KEY_NUM0 + (kc - KeyEvent.KEYCODE_0);
        if (kc >= KeyEvent.KEYCODE_NUMPAD_0 && kc <= KeyEvent.KEYCODE_NUMPAD_9) {
            return Canvas.KEY_NUM0 + (kc - KeyEvent.KEYCODE_NUMPAD_0);
        }
        return 0;
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.getRepeatCount() == 0) handleBackDown();
            return true;
        }
        int k = mapKey(keyCode);
        if (k != 0 && gameView != null) {
            if (event.getRepeatCount() == 0) gameView.pressKey(k);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (gameView != null) gameView.releaseKey(GameView.K_RSH);
            return true;
        }
        int k = mapKey(keyCode);
        if (k != 0 && gameView != null) {
            gameView.releaseKey(k);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }
}
