package org.dyndns.fules.ck;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader.TileMode;
import android.graphics.Typeface;
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener;
import android.inputmethodservice.KeyboardView;
import android.media.MediaPlayer;
import android.net.Uri;
import android.text.InputType;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View.MeasureSpec;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

interface EmbeddableItem {
	public void calculateSizes();
}

/*
 * <Action> tag
 */
class Action {
	int		keyCode, layout;
	String		code, mod, text, cmd;
	boolean		isLock, isEmpty, isSpecial;

	public Action(XmlPullParser parser) throws XmlPullParserException, IOException {
		int n = 0;
		String s;

		if ((parser.getEventType() != XmlPullParser.START_TAG) || !parser.getName().contentEquals("Action"))
			throw new XmlPullParserException("Expected <Action>", parser, null);

		isLock = isEmpty = false;
		keyCode = layout = -1;

		s = parser.getAttributeValue(null, "key");
		if (s != null) {
			keyCode = Integer.parseInt(s);
			n++;
		}

		s = parser.getAttributeValue(null, "layout");
		if (s != null) {
			layout = Integer.parseInt(s);
			n++;
		}

		code = parser.getAttributeValue(null, "code");
		if (code != null) {
			code = java.net.URLDecoder.decode(code);
			n++;
		}

		s = parser.getAttributeValue(null, "cmd");
		if (s != null) {
			cmd = s;
			n++;
		}

		String lock = parser.getAttributeValue(null, "lock");
		if (lock != null) {
			isLock = true;
			n++;
		}

		mod = parser.getAttributeValue(null, "mod");
		if (mod != null)
			n++;

		if (n > 1)
			throw new XmlPullParserException("Action: at most one of key, code, cmd, mod or lock may be present", parser, null);

		if (n == 0) {
			isEmpty = true;
		}
		else {
			if (isLock)
				mod = lock;

			text = parser.getAttributeValue(null, "text");
			if (text != null)
				text = java.net.URLDecoder.decode(text);
			else if (code != null)
				text = code;
			else if (cmd != null)
				text = cmd;
			else if (mod != null)
				text = mod;
			else if (keyCode >= 0)
				text = 'K'+String.valueOf(keyCode);
			else 
				text = 'L'+String.valueOf(layout);
		}

		isSpecial = (cmd != null) || (mod != null) || (layout != -1);
		s = parser.getAttributeValue(null, "isSpecial");
		if (s != null) {
			isSpecial = Integer.parseInt(s) != 0;
		}

		parser.nextTag();
		if ((parser.getEventType() != XmlPullParser.END_TAG) || !parser.getName().contentEquals("Action"))
			throw new XmlPullParserException("Expected </Action>", parser, null);
		parser.nextTag();
	}

}

/*
 * <Layout> tag
 */
public class CompassKeyboardView extends FrameLayout {
	// Constants
	private static final String		TAG = "CompassKeyboardView";
	private static final long[][]		vibratePattern = { { 10, 100 }, { 10, 50, 50, 50 } };
	private static final int		LONG_TAP_TIMEOUT = 1200;	// timeout in msec after which a tap is considered a long one
	private static final String		TICK_SOUND_FILE = "/system/media/audio/ui/Effect_Tick.ogg";
	//private static final String		TICK_SOUND_FILE = "/system/media/audio/ui/KeypressStandard.ogg";
	public static final int			NONE	= -1;
	public static final int			NW	= 0;
	public static final int			N	= 1;
	public static final int			NE	= 2;
	public static final int			W	= 3;
	public static final int			TAP	= 4;
	public static final int			E	= 5;
	public static final int			SW	= 6;
	public static final int			S	= 7;
	public static final int			SE	= 8;
	public static final String[]		globalSwipeSign = { "⇖", "⇑", "⇗", "⇐", "╳", "⇒", "⇙", "⇓", "⇘" };

	public static final int			FEEDBACK_HIGHLIGHT	= 1;
	public static final int			FEEDBACK_TOAST		= 2;

	// Parameters
	int					vibrateOnKey = 0;
	int					vibrateOnModifier = 0;
	int					vibrateOnCancel = 0;
	int					feedbackNormal = 0;
	int					feedbackPassword = 0;
	float					keyMM = 12;	// maximal key size in mm-s
	float					marginLeft = 0, marginRight = 0, marginBottom = 0; // margins in mm-s

	// Internal params
	int					nColumns;	// maximal number of symbol columns (eg. 3 for full key, 2 for side key), used for size calculations
	int					nKeys;		// maximal number of keys per row, used for calculating with the gaps between keys
	int					sym, gap;	// size of symbols on keys and gap between them (in pixels)
	float					fontSize;	// height of the key caption font in pixels
	float					fontDispY;	// Y-displacement of key caption font (top to baseline)
	boolean					isTypingPassword; // is the user typing a password

	Vibrator				vibro;		// vibrator
	Paint					textPaint;	// Paint for drawing key captions
	Paint					specPaint;	// Paint for drawing special key captions
	Paint					candidatePaint;	// Paint for drawing candidate key captions
	KeyboardView.OnKeyboardActionListener	actionListener;	// owner of the callback methods, result is passed to this instance
	Action[]				globalDir;	// global swipe <Action>s
	int					candidateGlobalDir = NONE;	// candidate global swipe direction
	HashSet<String>				modifiers;	// currently active modifiers
	HashSet<String>				locks;		// currently active locks
	HashSet<String>				effectiveMods;	// currently active effective modifiers (== symmetric difference of modifiers and locks)
	LinearLayout.LayoutParams		lp;		// layout params for placing the rows
	LinearLayout				kbd;  		// the keyboard layer
	OverlayView				overlay;	// the overlay layer

	LongTap					onLongTap;	// long tap checker
	boolean					wasLongTap;	// marker to skip processing the release of a long tap
	Toast					toast;
	float					downX, downY, upX, upY;	// the coordinates of a swipe, used for recognising global swipes

	MediaPlayer				tick;		// for playing tick sound

	/*
	 * Long tap handler
	 */
	private final class LongTap implements Runnable {
		public void run() {
			wasLongTap = processAction(globalDir[TAP]);
		}
	}

	/*
	 * <Align> tag
	 */
	class Align extends View implements EmbeddableItem {
		int width, height;
		int xmax, ymax;

		public Align(Context context, XmlPullParser parser) throws XmlPullParserException, IOException {
			super(context);
			String s;

			if ((parser.getEventType() != XmlPullParser.START_TAG) || !parser.getName().contentEquals("Align"))
				throw new XmlPullParserException("Expected <Align>", parser, null);

			width = height = xmax = ymax = 0;

			s = parser.getAttributeValue(null, "width");
			if (s != null)
				width = Integer.parseInt(s);

			s = parser.getAttributeValue(null, "height");
			if (s != null)
				height = Integer.parseInt(s);

			parser.nextTag();
			if ((parser.getEventType() != XmlPullParser.END_TAG) || !parser.getName().contentEquals("Align"))
				throw new XmlPullParserException("Expected </Align>", parser, null);
			parser.nextTag();
		}

		// Recalculate the drawing coordinates according to the symbol size
		public void calculateSizes() {
			xmax = Math.round(width * (sym + gap));
			ymax = Math.round(height * fontSize);
		}

		// Report the size of the alignment
		@Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			int w = (View.MeasureSpec.getMode(widthMeasureSpec) == View.MeasureSpec.EXACTLY) ? View.MeasureSpec.getSize(widthMeasureSpec) : xmax;
			int h = (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.EXACTLY) ? View.MeasureSpec.getSize(heightMeasureSpec) : ymax;
			setMeasuredDimension(w, h);
		}
	}

	/*
	 * <Row> tag
	 */ 
	class Row extends LinearLayout implements EmbeddableItem {
		int	ymax;					// height of the row
		int	y1, y2, y3;				// y positions of the symbol rows within the keys
		int	columns;				// number of symbol columns (eg. 3 for full key, 2 for side key), used for size calculations
		Paint	buttonPaint;				// Paint used for drawing the key background
		Paint	framePaint;				// Paint used for drawing the key frame
		boolean	hasTop, hasBottom;			// does all the keys in the row have tops and bottoms?

		/*
		 * <Key> tag
		 */
		class Key extends View implements EmbeddableItem {
			RectF			fullRect;		// the rectangle of the key frame
			RectF			innerRect;		// the rectangle of the key body
			int			xmax;			// width of the key
			int			x1, x2, x3;		// x positions of the symbol columns within the key
			ArrayList<State>	state;			// the modifier <State> tags within this <Key>
			State			currentState = null;	// the currently selected <State>, according to @CompassKeyboardView::modifiers
			boolean			hasLeft, hasRight;	// does the key have the given left and right symbols?
			int			candidateDir;		// the direction into which a drag is in progress, or NONE if inactive

			/*
			 * <State> tag
			 */
			class State {
				HashSet<String>	nameSet;	// the modifier names that this <State> is representing
				Action[]	dir;		// the <Action>-s that are valid in this state

				/*
				 * Methods of State
				 */

				public State(XmlPullParser parser) throws XmlPullParserException, IOException {
					if ((parser.getEventType() != XmlPullParser.START_TAG) || !parser.getName().contentEquals("State"))
						throw new XmlPullParserException("Expected <State>", parser, null);

					nameSet = new HashSet();
					dir = new Action[9];

					String name = parser.getAttributeValue(null, "name");
					if ((name != null) && !name.contentEquals("")) {
						String[] fields = name.split(",");
						int numFields = fields.length;
						for (int i = 0; i < numFields; i++)
							nameSet.add(fields[i]);
					}

					parser.nextTag();

					if (hasTop) {
						if (hasLeft)
							dir[NW] = new Action(parser);
						dir[N] = new Action(parser);
						if (hasRight)
							dir[NE] = new Action(parser);
					}
					if (hasLeft)
						dir[W] = new Action(parser);
					dir[TAP] = new Action(parser);
					if (hasRight)
						dir[E] = new Action(parser);
					if (hasBottom) {
						if (hasLeft)
							dir[SW] = new Action(parser);
						dir[S] = new Action(parser);
						if (hasRight)
							dir[SE] = new Action(parser);
					}

					// delete empty entries
					for (int i = 0; i < 9; i++) {
						if ((dir[i] != null) && dir[i].isEmpty)
							dir[i] = null;
					}

					if ((parser.getEventType() != XmlPullParser.END_TAG) || !parser.getName().contentEquals("State"))
						throw new XmlPullParserException("Expected </State>", parser, null);
					parser.nextTag();
				}
			}

			/*
			 * Methods of Key
			 */

			public Key(Context context, XmlPullParser parser) throws XmlPullParserException, IOException {
				super(context);

				String s;

				if ((parser.getEventType() != XmlPullParser.START_TAG) || !parser.getName().contentEquals("Key"))
					throw new XmlPullParserException("Expected <Key>", parser, null);

				hasLeft = hasRight = true;
				state = new ArrayList();

				//s = parser.getAttributeValue(null, "name");
				//if (s != null)
				//	Log.d(TAG, "Loading key '"+s+"'");

				s = parser.getAttributeValue(null, "has_left");
				if ((s != null) && (Integer.parseInt(s) == 0))
					hasLeft = false;

				s = parser.getAttributeValue(null, "has_right");
				if ((s != null) && (Integer.parseInt(s) == 0))
					hasRight = false;

				parser.nextTag();
				while (parser.getEventType() != XmlPullParser.END_TAG) {
					State st = new State(parser);
					state.add(st);
				}

				if (!parser.getName().contentEquals("Key"))
					throw new XmlPullParserException("Expected </Key>", parser, null);
				parser.nextTag();

				candidateDir = NONE;
			}

			// Search a State that represents searchSet and set currentState accordingly (null if there is no such state)
			public void setState(HashSet<String> searchSet) {
				if ((currentState == null) || !currentState.nameSet.equals(searchSet)) {
					int n = state.size();
					currentState = null;
					for (int i = 0; (i < n) && (currentState == null); i++) {
						State st = state.get(i);
						if (st.nameSet.equals(searchSet))
							currentState = st;
					}
					setCandidate(NONE);
					invalidate();
				}
			}

			// Recalculate the drawing coordinates according to the symbol size
			public void calculateSizes() {
				if (hasLeft) {
					if (hasRight) {
						xmax	= Math.round(4 * gap + 3    * sym);
						x1	= Math.round(    gap + 0.5f * sym);
						x2	= Math.round(2 * gap + 1.5f * sym);
						x3	= Math.round(3 * gap + 2.5f * sym);
					}
					else {
						xmax	= Math.round(3 * gap + 2    * sym);
						x1	= Math.round(    gap + 0.5f * sym);
						x2 = x3	= Math.round(2 * gap + 1.5f * sym);
					}
				}
				else {
					if (hasRight) {
						xmax	= Math.round(3 * gap + 2    * sym);
						x1 = x2	= Math.round(    gap + 0.5f * sym);
						x3	= Math.round(2 * gap + 1.5f * sym);
					}
					else {
						xmax	= Math.round(2 * gap +        sym);
						x1 = x2 = x3 = Math.round(gap + 0.5f * sym);
					}
				}
				fullRect = new RectF(0, 0, xmax - 1, ymax - 1);
				innerRect = new RectF(2, 2, xmax - 3, ymax - 3);
			}

			void setCandidate(int d) {
				if (candidateDir == d)
					return;
				candidateDir = d;
				if (currentState == null)
					return;
				switch (isTypingPassword ? feedbackPassword : feedbackNormal) {
					case FEEDBACK_HIGHLIGHT:
						/*Action cd = (0 <= d) ? currentState.dir[d] : null;
						overlay.setCandidate((cd != null) ? cd.text : null);
						overlay.invalidate(); */
						invalidate();
						break;

					case FEEDBACK_TOAST:
						Action cd = (0 <= d) ? currentState.dir[d] : null;
						if (cd != null) {
							toast.setText(cd.text);
							toast.show();
						}
						else {
							toast.cancel();
						}
						break;
				}
			}

			// figure out the direction of the swipe
			int getDirection(float x, float y) {
				int d;
				float dx = (x - downX) * 2;
				float dy = (y - downY) * 2;

				if (dx < -xmax) {
					if (dy < -ymax)
						d = NW;
					else if (dy < ymax)
						d = W;
					else
						d = SW;
				}
				else if (dx < xmax) {
					if (dy < -ymax)
						d = N;
					else if (dy < ymax)
						d = TAP;
					else
						d = S;
				}
				else {
					if (dy < -ymax)
						d = NE;
					else if (dy < ymax)
						d = E;
					else
						d = SE;
				}

				return d;
			}

			// Touch event handler
			@Override public boolean onTouchEvent(MotionEvent event) {
				float t, l;
				int d;
				boolean res = processTouchEvent(event);
				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						setCandidate(getDirection(event.getX(), event.getY()));
						return true;

					case MotionEvent.ACTION_UP:
						setCandidate(NONE);
						setGlobalCandidate(NONE);

						// check if global: transform to the basis of the CompassKeyboardView and ask it to decide
						l = getLeft() + Row.this.getLeft();
						t = getTop() + Row.this.getTop();
						d = getGlobalSwipeDirection(downX + l, downY + t, upX + l, upY + t);
						if ((d != NONE) && processAction(globalDir[d]))
							return true;

						// if the key is not valid in this state or there is no corresponding Action for it, then release the modifiers
						if ((currentState == null) || !processAction(currentState.dir[getDirection(upX, upY)]))
							changeState(null, false);
						// touch event processed
						return true;

					case MotionEvent.ACTION_MOVE:
						// check if global: transform to the basis of the CompassKeyboardView and ask it to decide
						l = getLeft() + Row.this.getLeft();
						t = getTop() + Row.this.getTop();
						d = getGlobalSwipeDirection(downX + l, downY + t, event.getX() + l, event.getY() + t);

						setCandidate((d == NONE) ? getDirection(event.getX(), event.getY()) : NONE);
						setGlobalCandidate(d);
						break;
				}
				return res;
			}

			// Draw a Action symbol label if it is specified
			protected void drawLabel(Canvas canvas, int d, int x, int y) {
				if (currentState.dir[d] != null)
					canvas.drawText(currentState.dir[d].text, x, y,
						(d == candidateDir) ? candidatePaint : currentState.dir[d].isSpecial ? specPaint : textPaint);
			}

			// Redraw the key
			@Override protected void onDraw(Canvas canvas) {
				// draw the background
				canvas.drawRoundRect(fullRect, 5, 5, framePaint);
				canvas.drawRoundRect(innerRect, 5, 5, buttonPaint);
				//canvas.drawRoundRect(fullRect, 2, 2, buttonPaint);	// for a plain one-gradient background

				// draw the Action labels if the key is valid in this state
				if (currentState != null) {
					drawLabel(canvas, NW,  x1, y1);
					drawLabel(canvas, N,   x2, y1);
					drawLabel(canvas, NE,  x3, y1);

					drawLabel(canvas, W,   x1, y2);
					drawLabel(canvas, TAP, x2, y2);
					drawLabel(canvas, E,   x3, y2);

					drawLabel(canvas, SW,  x1, y3);
					drawLabel(canvas, S,   x2, y3);
					drawLabel(canvas, SE,  x3, y3);
				}
			}

			// Report the size of the key
			@Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
				// policy: if not specified by the parent as EXACTLY, use our own ideas
				int w = (View.MeasureSpec.getMode(widthMeasureSpec) == View.MeasureSpec.EXACTLY) ? View.MeasureSpec.getSize(widthMeasureSpec) : xmax;
				int h = (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.EXACTLY) ? View.MeasureSpec.getSize(heightMeasureSpec) : ymax;
				setMeasuredDimension(w, h);
			}
		}

		/*
		 * Methods of Row
		 */

		public Row(Context context, XmlPullParser parser) throws XmlPullParserException, IOException {
			super(context);

			String s;

			setOrientation(android.widget.LinearLayout.HORIZONTAL);
			setGravity(android.view.Gravity.CENTER);
			//setMinimumWidth(10000); // this is one way for horizontal maximising
			//setBackgroundColor(0xff3f0000); // for debugging placement

			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			lp.setMargins(1, 0, 1, 0);

			buttonPaint = new Paint();
			buttonPaint.setAntiAlias(true);
			buttonPaint.setColor(Color.DKGRAY);

			framePaint = new Paint();
			framePaint.setAntiAlias(true);
			framePaint.setColor(Color.LTGRAY);

			hasTop = hasBottom = true;

			int eventType = parser.getEventType();
			if ((parser.getEventType() != XmlPullParser.START_TAG) || !parser.getName().contentEquals("Row"))
				throw new XmlPullParserException("Expected <Row>", parser, null);

			s = parser.getAttributeValue(null, "has_top");
			if ((s != null) && (Integer.parseInt(s) == 0))
				hasTop = false;

			s = parser.getAttributeValue(null, "has_bottom");
			if ((s != null) && (Integer.parseInt(s) == 0))
				hasBottom = false;

			parser.nextTag();
			columns = 0;
			while (parser.getEventType() != XmlPullParser.END_TAG) {
				if (parser.getEventType() != XmlPullParser.START_TAG)
					throw new XmlPullParserException("Expected content TAG", parser, null);

				if (parser.getName().contentEquals("Key")) {
					Key nk = new Key(getContext(), parser);

					columns++;
					if (nk.hasLeft)
						columns++;
					if (nk.hasRight)
						columns++;
					addView(nk, lp);
				}
				else if (parser.getName().contentEquals("Align")) {
					Align na = new Align(getContext(), parser);

					columns += na.width;
					addView(na, lp);
				}
			}
			if (!parser.getName().contentEquals("Row"))
				throw new XmlPullParserException("Expected </Row>", parser, null);
			parser.nextTag();

		}

		// Set the contained Keys to the State appropriate for @modifiers
		public void setState(HashSet<String> modifiers) {
			int n = getChildCount();
			for (int i = 0; i < n; i++) {
				View v = getChildAt(i);
				if (v instanceof Key)
					((Key)v).setState(modifiers);
			}
		}

		// Recalculate the drawing coordinates according to the symbol size
		public void calculateSizes() {
			if (hasTop) {
				if (hasBottom) {
					ymax	= Math.round(2 * gap + 3 * fontSize);
					y1	= Math.round(    gap +                fontDispY);
					y2	= Math.round(    gap + 1 * fontSize + fontDispY);
					y3	= Math.round(    gap + 2 * fontSize + fontDispY);
				}
				else {
					ymax	= Math.round(2 * gap + 2 * fontSize);
					y1	= Math.round(    gap +                fontDispY);
					y2 = y3	= Math.round(    gap + 1 * fontSize + fontDispY);
				}
			}
			else {
				if (hasBottom) {
					ymax	= Math.round(2 * gap + 2 * fontSize);
					y1 = y2	= Math.round(    gap +                fontDispY);
					y3	= Math.round(    gap + 1 * fontSize + fontDispY);
				}
				else {
					ymax	= Math.round(2 * gap + fontSize);
					y1 = y2 = y3 = Math.round(gap +         fontDispY);
				}
			}

			// Set the key background color
			buttonPaint.setShader(new LinearGradient(0, 0, 0, ymax, 0xff696969, 0xff0a0a0a, android.graphics.Shader.TileMode.CLAMP));
			framePaint.setShader(new LinearGradient(0, 0, 0, ymax, 0xff787878, 0xff000000, android.graphics.Shader.TileMode.CLAMP));

			int n = getChildCount();
			for (int i = 0; i < n; i++) {
				EmbeddableItem e = (EmbeddableItem)getChildAt(i);
				e.calculateSizes();
			}
		}

		// Report the size of the row
		@Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			// if the parent specified only a maximal width, make the row span the whole of it
			if (android.view.View.MeasureSpec.getMode(widthMeasureSpec) == android.view.View.MeasureSpec.AT_MOST) {
				widthMeasureSpec = android.view.View.MeasureSpec.makeMeasureSpec(
						android.view.View.MeasureSpec.EXACTLY,
						android.view.View.MeasureSpec.getSize(widthMeasureSpec));

			}
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		}
	}

	/*
	 * Methods of OverlayView
	 */

	class OverlayView extends View {
		String candidate = null;
		int candidateDir = NONE;

		public OverlayView(Context context) {
			super(context);
		}

		@Override protected void onDraw(Canvas canvas) {
			int w = canvas.getWidth();
			int h = canvas.getHeight();
			int cx = w / 2;
			int cy = h / 2;

			switch (candidateDir) {
				case NW:
				case SE:
					canvas.drawLine(0, 0, w, h, candidatePaint);
					break;

				case N:
				case S:
					canvas.drawLine(cx, 0, cx, h, candidatePaint);
					break;

				case NE:
				case SW:
					canvas.drawLine(w, 0, 0, h, candidatePaint);
					break;

				case W:
				case E:
					canvas.drawLine(0, cy, w, cy, candidatePaint);
					break;

				case NONE:
					if ((candidate != null) && (candidate.length() > 0))
						canvas.drawText(candidate, cx, cy, candidatePaint);
					break;

				default:
					break;
			}
		}

		@Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			setMeasuredDimension(kbd.getWidth(), kbd.getHeight());
		}

		void setCandidate(String s) {
			candidate = s;
			invalidate();
		}

		void setGlobalCandidate(int d) {
			candidateDir = d;
			invalidate();
		}
	}

	/*
	 * Methods of CompassKeyboardView
	 */

	public CompassKeyboardView(Context context) {
		super(context);
		kbd = new LinearLayout(context);
		kbd.setOrientation(android.widget.LinearLayout.VERTICAL);
		kbd.setGravity(android.view.Gravity.TOP);
		//kbd.setBackgroundColor(0xff003f00); // for debugging placement
		addView(kbd);

		overlay = new OverlayView(context);
		addView(overlay);

		lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		lp.setMargins(0, 1, 0, 1);

		textPaint = new Paint();
		textPaint.setAntiAlias(true);
		textPaint.setColor(Color.WHITE);
		textPaint.setTextAlign(Paint.Align.CENTER);
		textPaint.setShadowLayer(3, 0, 2, 0xff000000);

		specPaint = new Paint();
		specPaint.setAntiAlias(true);
		specPaint.setColor(Color.CYAN);
		specPaint.setTextAlign(Paint.Align.CENTER);
		specPaint.setShadowLayer(3, 0, 2, 0xff000000);

		candidatePaint = new Paint();
		candidatePaint.setAntiAlias(true);
		candidatePaint.setColor(Color.YELLOW);
		candidatePaint.setTextAlign(Paint.Align.CENTER);
		candidatePaint.setShadowLayer(3, 0, 2, 0xff000000);

		candidatePaint = new Paint();
		candidatePaint.setAntiAlias(true);
		candidatePaint.setColor(Color.YELLOW);
		candidatePaint.setTextAlign(Paint.Align.CENTER);
		candidatePaint.setShadowLayer(3, 0, 2, 0xff000000);

		vibro = (Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
		tick = MediaPlayer.create(context, Uri.parse(TICK_SOUND_FILE));
		onLongTap = new LongTap();
		modifiers = new HashSet();
		locks = new HashSet();
		effectiveMods = new HashSet();

		toast = Toast.makeText(context, "<none>", Toast.LENGTH_SHORT);
		toast.setGravity(Gravity.BOTTOM, 0, 0);
	}

	void vibrateCode(int n) {
		if (n == -1) 
			tick.start();
		else if ((n >= 0) && (n < vibratePattern.length))
			vibro.vibrate(vibratePattern[n], -1);
	}

	// Read the layout from an XML parser
	public void readLayout(XmlPullParser parser) throws XmlPullParserException, IOException {
		int eventType = parser.getEventType();
		if ((parser.getEventType() != XmlPullParser.START_TAG) || !parser.getName().contentEquals("Layout"))
			throw new XmlPullParserException("Expected <Layout>", parser, null);

		parser.nextTag();

		// read the global swipes
		globalDir = new Action[9];
		globalDir[NW]  = new Action(parser);
		globalDir[N]   = new Action(parser);
		globalDir[NE]  = new Action(parser);
		globalDir[W]   = new Action(parser);
		globalDir[TAP] = new Action(parser);
		globalDir[E]   = new Action(parser);
		globalDir[SW]  = new Action(parser);
		globalDir[S]   = new Action(parser);
		globalDir[SE]  = new Action(parser);
		for (int i = 0; i < 9; i++) {
			if ((globalDir[i] != null) && globalDir[i].isEmpty)
				globalDir[i] = null;
		}

		// drop and re-read all previously existing rows
		kbd.removeAllViews();
		nColumns = nKeys = 0;
		while (parser.getEventType() != XmlPullParser.END_TAG) {
			if (parser.getEventType() != XmlPullParser.START_TAG)
				throw new XmlPullParserException("Expected content tag", parser, null);

			if (parser.getName().contentEquals("Row")) {
				Row nr = new CompassKeyboardView.Row(getContext(), parser);
				kbd.addView(nr, lp);

				int nc = nr.getChildCount();
				if (nColumns < nr.columns)
					nColumns = nr.columns;
				if (nKeys < nc)
					nKeys = nc;
			}
			else if (parser.getName().contentEquals("Align")) {
				Align na = new Align(getContext(), parser);
				kbd.addView(na, lp);

				if (nColumns < na.width)
					nColumns = na.width;
			}
		}
		if (!parser.getName().contentEquals("Layout"))
			throw new XmlPullParserException("Expected </Layout>", parser, null);
		parser.nextTag();

		// recalculate sizes and set bg colour
		calculateSizesForMetrics(getResources().getDisplayMetrics());
	}

	// Recalculate all the sizes according to the display metrics
	public void calculateSizesForMetrics(DisplayMetrics metrics) {
		// note: the metrics may change during the lifetime of the instance, so these precalculations could not be done in the constructor
		int i, totalWidth; 
		int marginPixelsLeft = Math.round(marginLeft * metrics.xdpi / 25.4f);
		int marginPixelsRight = Math.round(marginRight * metrics.xdpi / 25.4f);
		int marginPixelsBottom = Math.round(marginBottom * metrics.ydpi / 25.4f);
		setPadding(marginPixelsLeft, 0, marginPixelsRight, marginPixelsBottom);

		Log.v(TAG, "keyMM=" + String.valueOf(keyMM) + ", xdpi=" + String.valueOf(metrics.xdpi) + ", ydpi=" + String.valueOf(metrics.ydpi) + ", nKeys=" + String.valueOf(nKeys) + ", nColumns=" + String.valueOf(nColumns));
		// Desired "key size" in pixels is keyMM * metrics.xdpi / 25.4f
                // This "key size" is an abstraction of a key that has 3 symbol columns (and therefore 4 gaps: gSgSgSg),
		// so that the gaps take up 1/3 and the symbols take up 2/3 of the key, so
		//   4*gaps = 1/3 * keySize	-> gap = keySize / 12
		//   3*sym = 2/3 * keySize	-> sym = 2 * keySize / 9
		// We have nKeys keys and nColumns columns, that means nKeys*gap + nColumns*(sym + gap), that is
		//   nKeys*keySize/12 + nColumns*keySize*(2/9 + 1/12) = keySize * (nKeys/12 + nColumns*11/36)
		totalWidth = Math.round(keyMM * metrics.xdpi / 25.4f * ((nKeys / 12.f) + (nColumns * 11 / 36.f)));
		// Regardless of keyMM, it must fit the metrics, that is width - margins - 1 pixel between keys
		i = metrics.widthPixels - marginPixelsLeft - marginPixelsRight - (nKeys - 1);
		Log.v(TAG, "totalWidth=" + String.valueOf(totalWidth) + ", max=" + String.valueOf(i));
		if (i < totalWidth)
			totalWidth = i;

		// Now back to real key sizes, we still have nKeys keys and nColumns columns for these totalWidth pixels, which means 
		//   nKeys*gap + nColumns*(sym + gap) = gap*(nKeys+nColumns) + sym*nColumns <= totalWidth

		// Rounding errors can foul up everything, and the sum above is more sensitive on the error of gap than of sym,
		//   so we calculate gap first (with rounding) and then adjust sym to it.
		// As decided, a gap to symbol ratio of 1/3 to 2/3 would be ergonomically pleasing, so 2*4*gap = 3*sym, that is sym = 8*gap/3, so
		//   gap*(nKeys+nColumns) + 8*gap/3*nColumns = totalWidth
		//   gap*(nKeys+nColumns + 8/3*nColumns) = totalWidth
		gap = Math.round(totalWidth / (nKeys+nColumns + 8*nColumns/3.f));
		// Calculating sym as 8/3*gap is tempting, but that wouldn't compensate the rounding error above, so we have to derive
		// it from totalWidth and rounding it only downwards:
		//   gap*(nKeys+nColumns) + sym*nColumns = totalWidth
		sym = (totalWidth - gap*(nKeys+nColumns)) / nColumns;
		Log.v(TAG, "sym=" + String.valueOf(sym) + ", gap=" + String.valueOf(gap));

		// Sample data: nKeys=5, columns=13; Galaxy Mini: 240x320, Ace: 320x480, S: 480x80, S3: 720x1280 

		// construct the Paint used for printing the labels
		textPaint.setTextSize(sym);
		specPaint.setTextSize(sym);
		candidatePaint.setTextSize(sym * 3 / 2);
		candidatePaint.setStrokeWidth(gap);

		Paint.FontMetrics fm = textPaint.getFontMetrics();
		fontSize = fm.descent - fm.ascent;
		fontDispY = -fm.ascent;
		Log.v(TAG, "reqFS="+String.valueOf(sym)+", fs="+String.valueOf(fontSize)+", asc="+String.valueOf(fm.ascent)+", desc="+String.valueOf(fm.descent));

		toast = Toast.makeText(getContext(), "<none>", Toast.LENGTH_SHORT); // FIXME: test
		toast.setGravity(Gravity.BOTTOM, 0, 0); // FIXME: test
		toast.setGravity(Gravity.TOP + Gravity.CENTER_HORIZONTAL, 0, -sym);

		int n = kbd.getChildCount();
		for (i = 0; i < n; i++) {
			EmbeddableItem e = (EmbeddableItem)kbd.getChildAt(i);
			e.calculateSizes();
		}
		commitState();
	}

	// Common touch event handler - record coordinates and manage long tap handlers
	public boolean processTouchEvent(MotionEvent event) {
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				// remember the swipe starting coordinates for checking for global swipes
				downX = event.getX();
				downY = event.getY();
				// register a long tap handler
				wasLongTap = false;
				postDelayed(onLongTap, LONG_TAP_TIMEOUT);
				return true;

			case MotionEvent.ACTION_UP:
				// end of swipe
				upX = event.getX();
				upY = event.getY();
				// check if this is the end of a long tap
				if (wasLongTap) {
					wasLongTap = false;
					return true;
				}
				// cancel any pending checks for long tap
				removeCallbacks(onLongTap);
				// touch event processed
				return true;

			case MotionEvent.ACTION_MOVE:
				// cancel any pending checks for long tap
				removeCallbacks(onLongTap);
				return false;
		}
		// we're not interested in other kinds of events
		return false;
	}

	@Override public boolean onTouchEvent(MotionEvent event) {
		boolean res = processTouchEvent(event);
		int d;

		switch (event.getAction()) {
			case MotionEvent.ACTION_UP:
				// check if global, done if it is
				d = getGlobalSwipeDirection(downX, downY, upX, upY);
				if ((d == NONE) || !processAction(globalDir[d]))
					changeState(null, false);
				break;

			case MotionEvent.ACTION_MOVE:
				d = getGlobalSwipeDirection(downX, downY, upX, upY);
				setGlobalCandidate(d);
				break;
		}
		return res;
	}

	public int getGlobalSwipeDirection(float x1, float y1, float x2, float y2) {
		float w = getWidth() / 3;
		float h = getHeight() / 4;
		int d, i1, j1, i2, j2;

		if (x1 < w)		i1 = -1;
		else if (x1 < (2 * w))	i1 = 0;
		else			i1 = 1;

		if (x2 < w)		i2 = -1;
		else if (x2 < (2 * w))	i2 = 0;
		else			i2 = 1;

		if (y1 < h)		j1 = -1;
		else if (y1 < (3 * h))	j1 = 0;
		else			j1 = 1;

		if (y2 < h)		j2 = -1;
		else if (y2 < (3 * h))	j2 = 0;
		else			j2 = 1;

		if ((i1 == 1) && (j1 == 1) && (i2 == -1) && (j2 == -1))
			return NW;
		else if ((i1 == 0) && (j1 == 1) && (i2 == 0) && (j2 == -1))
			return N;
		else if ((i1 == -1) && (j1 == 1) && (i2 == 1) && (j2 == -1))
			return NE;
		else if ((i1 == 1) && (j1 == 0) && (i2 == -1) && (j2 == 0))
			return W;
		else if ((i1 == -1) && (j1 == 0) && (i2 == 1) && (j2 == 0))
			return E;
		else if ((i1 == 1) && (j1 == -1) && (i2 == -1) && (j2 == 1))
			return SW;
		else if ((i1 == 0) && (j1 == -1) && (i2 == 0) && (j2 == 1))
			return S;
		else if ((i1 == -1) && (j1 == -1) && (i2 == 1) && (j2 == 1))
			return SE;
		else
			return NONE;
	}

	void setGlobalCandidate(int d) {
		if (candidateGlobalDir == d)
			return;
		candidateGlobalDir = d;
		switch (isTypingPassword ? feedbackPassword : feedbackNormal) {
			case FEEDBACK_HIGHLIGHT:
				if (overlay != null)
					overlay.setGlobalCandidate(d);
				break;

			case FEEDBACK_TOAST:
				String s = (0 <= d) ? globalSwipeSign[d] : null;
				if (s != null) {
					toast.setText(s);
					toast.show();
				}
				else {
					toast.cancel();
				}
				break;
		}
	}

	public void setOnKeyboardActionListener(KeyboardView.OnKeyboardActionListener listener) {
		actionListener = listener;
	}

	public void setInputType(int type) {
		isTypingPassword = ((type & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT) &&
			((type & InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_PASSWORD);
	}

	public void commitState() {
		// calculate the effectiveMods (== symmetric difference of locks and modifiers)
		effectiveMods = (HashSet<String>)modifiers.clone();
		Iterator<String> iter = locks.iterator();
		while (iter.hasNext()) {
			String s = iter.next();
			if (!effectiveMods.add(s))
				effectiveMods.remove(s);
		}

		int n = kbd.getChildCount();
		for (int i = 0; i < n; i++) {
			View v = kbd.getChildAt(i);
			if (v instanceof Row)
				((Row)v).setState(effectiveMods);
		}
	}

	private boolean processAction(Action cd) {
		toast.cancel();
		if (cd == null)
			return false;

		if (cd.mod != null) {
			changeState(cd.mod, cd.isLock);		// process a 'mod' or 'lock'
		}
		else if (actionListener != null) {
			resetState();
			if (cd.code != null)
				actionListener.onText(cd.code); // process a 'code'
			else if (cd.keyCode >= 0)
				actionListener.onKey(cd.keyCode, null); // process a 'key'
			else if (actionListener instanceof CompassKeyboard) {
				CompassKeyboard ck = (CompassKeyboard)actionListener;
				if (cd.layout >= 0)
					ck.updateLayout(cd.layout); // process a 'layout'
				else if ((cd.cmd != null) && (cd.cmd.length() > 0))
					ck.execCmd(cd.cmd); // process a 'cmd'
			}
			vibrateCode(vibrateOnKey);
		}

		return true;
	}

	public boolean checkState(String mod) {
		return modifiers.contains(mod);
	}
	public void resetState() {
		if (!modifiers.isEmpty()) {
			modifiers.clear();
			commitState();
		}
	}

	public void addState(String name) {
		if (modifiers.add(name))
			commitState();
	}

	public void toggleLock(String name) {
		if (!locks.add(name))
			locks.remove(name);
		commitState();
	}

	public void changeState(String state, boolean isLock) {
		if (state == null) {
			resetState();
			vibrateCode(vibrateOnCancel);
		}
		else if (state.contentEquals("hide")) {
			resetState();
			if (actionListener != null)
				actionListener.swipeDown(); // simulate hiding request

		}
		else if (isLock){
			resetState();
			toggleLock(state);
			vibrateCode(vibrateOnModifier);
		}
		else {
			addState(state);
			vibrateCode(vibrateOnModifier);
		}
	}

	public void setVibrateOnKey(int n) {
		vibrateOnKey = n;
	}

	public void setVibrateOnModifier(int n) {
		vibrateOnModifier = n;
	}

	public void setVibrateOnCancel(int n) {
		vibrateOnCancel = n;
	}

	public void setFeedbackNormal(int n) {
		feedbackNormal = n;
	}

	public void setFeedbackPassword(int n) {
		feedbackPassword = n;
	}

	public void setLeftMargin(float f) {
		marginLeft = f;
		calculateSizesForMetrics(getResources().getDisplayMetrics());
	}

	public void setRightMargin(float f) {
		marginRight = f;
		calculateSizesForMetrics(getResources().getDisplayMetrics());
	}

	public void setBottomMargin(float f) {
		marginBottom = f;
		calculateSizesForMetrics(getResources().getDisplayMetrics());
	}

	public void setMaxKeySize(float f) {
		keyMM = f > 0 ? f : 12;
		calculateSizesForMetrics(getResources().getDisplayMetrics());
	}
}

// vim: set ai si sw=8 ts=8 noet:
