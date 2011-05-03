package org.dyndns.fules.ck;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader.TileMode;
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener;
import android.inputmethodservice.KeyboardView;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View.MeasureSpec;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/*
 * <Action> tag
 */
class Action {
	int		keyCode;
	String		code, mod, text, s;
	boolean		isLock, isEmpty;

	public Action(XmlPullParser parser) throws XmlPullParserException, IOException {
		int n = 0;

		if ((parser.getEventType() != XmlPullParser.START_TAG) || !parser.getName().contentEquals("Action"))
			throw new XmlPullParserException("Expected <Action>", parser, null);

		isLock = isEmpty = false;

		s = parser.getAttributeValue(null, "key");
		if (s != null) {
			keyCode = Integer.parseInt(s);
			n++;
		}

		code = parser.getAttributeValue(null, "code");
		if (code != null) {
			code = java.net.URLDecoder.decode(code);
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
			throw new XmlPullParserException("Action: at most one of key, code, mod or lock may be present", parser, null);

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
			else if (mod != null)
				text = mod;
			else
				text = String.valueOf(keyCode);
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
public class CompassKeyboardView extends LinearLayout {
	// Constants
	private static final String		TAG = "CompassKeyboardView";
	private static final long[][]		vibratePattern = { { 10, 100 }, { 10, 50, 50, 50 } };
	private static final int		LONG_TAP_TIMEOUT = 1200;	// timeout in msec after which a tap is considered a long one
	public static final int			NW	= 0;
	public static final int			N	= 1;
	public static final int			NE	= 2;
	public static final int			W	= 3;
	public static final int			TAP	= 4;
	public static final int			E	= 5;
	public static final int			SW	= 6;
	public static final int			S	= 7;
	public static final int			SE	= 8;

	// Parameters
	int					vibrateOnKey = 0;
	int					vibrateOnModifier = 0;
	int					vibrateOnCancel = 0;
	float					keyMM;				// maximal key size in mm-s

	// Internal params
	int					columns;	// maximal number of symbol columns (eg. 3 for full key, 2 for side key), used for size calculations
	int					nKeys;		// maximal number of keys per row, used for calculating with the gaps between keys
	float					gap;		// gap between keys in pixels
	float					fontSize;	// height of the key caption font in pixels
	float					fontDispY;	// Y-displacement of key caption font (top to baseline)

	Vibrator				vibro;		// vibrator
	Paint					textPaint;	// Paint for drawing key captions
	KeyboardView.OnKeyboardActionListener	actionListener;	// owner of the callback methods, result is passed to this instance
	Action[]				dir;		// global swipe <Action>s
	HashSet<String>				modifiers;	// currently active modifiers
	HashSet<String>				locks;		// currently active locks
	HashSet<String>				effectiveMods;	// currently active effective modifiers (== symmetric difference of modifiers and locks)
	LinearLayout.LayoutParams		lp;		// layout params for placing the rows

	LongTap					onLongTap;	// long tap checker
	boolean					wasLongTap;	// marker to skip processing the release of a long tap

	/*
	 * <Row> tag
	 */ 
	class Row extends LinearLayout {
		int	ymax;					// height of the row
		int	y1, y2, y3;				// y positions of the symbol rows within the keys
		int	columns;				// number of symbol columns (eg. 3 for full key, 2 for side key), used for size calculations
		Paint	buttonPaint;				// Paint used for drawing the key background
		Paint	framePaint;				// Paint used for drawing the key frame
		boolean	hasTop, hasBottom;			// does all the keys in the row have tops and bottoms?

		/*
		 * <Key> tag
		 */
		class Key extends View {
			RectF			fullRect;		// the rectangle of the key frame
			RectF			innerRect;		// the rectangle of the key body
			float			downX, downY;		// the coordinates of the start of a swipe, used for recognising global swipes
			int			xmax;			// width of the key
			int			x1, x2, x3;		// x positions of the symbol columns within the key
			ArrayList<State> 	state;			// the modifier <State> tags within this <Key>
			State			currentState = null;	// the currently selected <State>, according to @CompassKeyboardView::modifiers
			boolean			hasLeft, hasRight;	// does the key have the given left and right symbols?

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
					int i;

					if ((parser.getEventType() != XmlPullParser.START_TAG) || !parser.getName().contentEquals("State")) {
						throw new XmlPullParserException("Expected <State>", parser, null);
					}

					nameSet = new HashSet();
					dir = new Action[9];

					String name = parser.getAttributeValue(null, "name");
					if ((name != null) && !name.contentEquals("")) {
						String[] fields = name.split(",");
						int numFields = fields.length;
						for (i = 0; i < numFields; i++) {
							nameSet.add(fields[i]);
						}
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
					for (i = 0; i < 9; i++) {
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

				int eventType, n, i;
				String s;

				eventType = parser.getEventType();
				if ((parser.getEventType() != XmlPullParser.START_TAG) || !parser.getName().contentEquals("Key"))
					throw new XmlPullParserException("Expected <Key>", parser, null);

				hasLeft = hasRight = true;
				state = new ArrayList();

				s = parser.getAttributeValue(null, "name");
				if (s != null)
					Log.d(TAG, "Loading key '"+s+"'");

				s = parser.getAttributeValue(null, "has_left");
				if (s != null) {
					i = Integer.parseInt(s);
					if (i == 0)
						hasLeft = false;
				}

				s = parser.getAttributeValue(null, "has_right");
				if (s != null) {
					i = Integer.parseInt(s);
					if (i == 0)
						hasRight = false;
				}

				parser.nextTag();
				while (parser.getEventType() != XmlPullParser.END_TAG) {
					State st = new State(parser);
					state.add(st);
				}

				if (!parser.getName().contentEquals("Key"))
					throw new XmlPullParserException("Expected </Key>", parser, null);
				parser.nextTag();
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
					invalidate();
				}
			}

			// Recalculate the drawing coordinates according to the symbol size
			void calculateSizes(float symbolSize) {
				if (hasLeft) {
					if (hasRight) {
						xmax	= Math.round(4 * gap + 3    * symbolSize);
						x1	= Math.round(    gap + 0.5f * symbolSize);
						x2	= Math.round(2 * gap + 1.5f * symbolSize);
						x3	= Math.round(3 * gap + 2.5f * symbolSize);
					}
					else {
						xmax	= Math.round(3 * gap + 2    * symbolSize);
						x1	= Math.round(    gap + 0.5f * symbolSize);
						x2 = x3	= Math.round(2 * gap + 1.5f * symbolSize);
					}
				}
				else {
					if (hasRight) {
						xmax	= Math.round(3 * gap + 2    * symbolSize);
						x1 = x2	= Math.round(    gap + 0.5f * symbolSize);
						x3	= Math.round(2 * gap + 1.5f * symbolSize);
					}
					else {
						xmax	= Math.round(2 * gap + symbolSize);
						x1 = x2 = x3 = Math.round(gap + 0.5f * symbolSize);
					}
				}
				fullRect = new RectF(0, 0, xmax - 1, ymax - 1);
				innerRect = new RectF(2, 2, xmax - 3, ymax - 3);
			}

			// Check if the swipe is a global one
			boolean processGlobalSwipe(float x1, float y1, float x2, float y2) {
				// transform to the basis of the Row and ask it to decide
				float l = getLeft();
				float t = getTop();
				return Row.this.processGlobalSwipe(x1 + l, y1 + t, x2 + l, y2 + t);
			}

			// Touch event handler
			@Override public boolean onTouchEvent(MotionEvent event) {
				int action = event.getAction();

				if (action == MotionEvent.ACTION_DOWN)
				{
					// remember the swipe starting coordinates for checking for global swipes
					downX = event.getX();
					downY = event.getY();

					// register a long tap handler
					wasLongTap = false;
					postDelayed(onLongTap, LONG_TAP_TIMEOUT);
					return true;
				}

				if (action == MotionEvent.ACTION_UP) {
					// check if this is the end of a long tap
					if (wasLongTap) {
						wasLongTap = false;
						return true;
					}

					// end of swipe
					float x = event.getX();
					float y = event.getY();

					// cancel any pending checks for long tap
					removeCallbacks(onLongTap);

					// check if global, done if it is
					if (processGlobalSwipe(downX, downY, x, y))
						return true;

					// unspecified keys are used for releasing modifier state, so we must recognise this case
					boolean processed = false;

					// if the key is valid in this state...
					if (currentState != null) {
						int d;

						// figure out the direction of the swipe
						if (x < 0) {
							if (y < 0)
								d = NW;
							else if (y < ymax)
								d = W;
							else
								d = SW;
						}
						else if (x < xmax) {
							if (y < 0)
								d = N;
							else if (y < ymax)
								d = TAP;
							else
								d = S;
						}
						else {
							if (y < 0)
								d = NE;
							else if (y < ymax)
								d = E;
							else
								d = SE;
						}
						
						// get the corresponding Action, if it is present
						if (processAction(currentState.dir[d]))
							processed = true; // the keystroke is valid
					}

					// if the swipe was not for a specified action, release the modifiers
					if (!processed)
						changeState(null, false);

					// touch event processed
					return true;
				}

				// we're not interested in other kinds of events
				return false;
			}

			// Draw a Action symbol label if it is specified
			protected void drawLabel(Canvas canvas, int d, int x, int y) {
				if (currentState.dir[d] != null)
					canvas.drawText(currentState.dir[d].text, x, y, textPaint);
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
				int w, h;

				if (View.MeasureSpec.getMode(widthMeasureSpec) == View.MeasureSpec.EXACTLY)
					w = View.MeasureSpec.getSize(widthMeasureSpec);
				else
					w = xmax;

				if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.EXACTLY)
					h = View.MeasureSpec.getSize(heightMeasureSpec);
				else
					h = ymax;

				setMeasuredDimension(w, h);
			}
		}

		/*
		 * Methods of Row
		 */

		public Row(Context context, XmlPullParser parser) throws XmlPullParserException, IOException {
			super(context);

			int i;
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
			if (s != null) {
				i = Integer.parseInt(s);
				if (i == 0)
					hasTop = false;
			}

			s = parser.getAttributeValue(null, "has_bottom");
			if (s != null) {
				i = Integer.parseInt(s);
				if (i == 0)
					hasBottom = false;
			}

			parser.nextTag();
			columns = 0;
			while (parser.getEventType() != XmlPullParser.END_TAG) {
				Key nk = new Key(getContext(), parser);

				columns++;
				if (nk.hasLeft)
					columns++;
				if (nk.hasRight)
					columns++;
				addView(nk, lp);
			}
			if (!parser.getName().contentEquals("Row"))
				throw new XmlPullParserException("Expected </Row>", parser, null);
			parser.nextTag();

		}

		// Set the contained Keys to the State appropriate for @modifiers
		public void setState(HashSet<String> modifiers) {
			int n = getChildCount();
			for (int i = 0; i < n; i++) {
				Key k = (Key)getChildAt(i);
				k.setState(modifiers);
			}
		}

		// Recalculate the drawing coordinates according to the symbol size
		public void calculateSizes(float symbolSize) {
			if (hasTop) {
				if (hasBottom) {
					ymax	= Math.round(2 * gap + 3 * fontSize);
					y1	= Math.round(gap +         fontDispY);
					y2	= Math.round(gap + 1 * fontSize + fontDispY);
					y3	= Math.round(gap + 2 * fontSize + fontDispY);
				}
				else {
					ymax	= Math.round(2 * gap + 2 * fontSize);
					y1	= Math.round(gap +         fontDispY);
					y2 = y3	= Math.round(gap + 1 * fontSize + fontDispY);
				}
			}
			else {
				if (hasBottom) {
					ymax	= Math.round(2 * gap + 2 * fontSize);
					y1 = y2	= Math.round(gap +         fontDispY);
					y3	= Math.round(gap + 1 * fontSize + fontDispY);
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
				Key k = (Key)getChildAt(i);
				k.calculateSizes(symbolSize);
			}
		}

		// Check if the swipe is a global one
		boolean processGlobalSwipe(float x1, float y1, float x2, float y2) {
			// transform to the basis of the CompassKeyboardView and ask it to decide
			float l = getLeft();
			float t = getTop();
			return CompassKeyboardView.this.processGlobalSwipe(x1 + l, y1 + t, x2 + l, y2 + t);
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
	 * Methods of CompassKeyboardView
	 */

	public CompassKeyboardView(Context context) {
		super(context);
		setOrientation(android.widget.LinearLayout.VERTICAL);
		setGravity(android.view.Gravity.TOP);
		//setBackgroundColor(0xff003f00); // for debugging placement
		
		lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		lp.setMargins(0, 1, 0, 1);

		textPaint = new Paint();
		textPaint.setAntiAlias(true);
		textPaint.setColor(Color.WHITE);
		textPaint.setTextAlign(Paint.Align.CENTER);
		textPaint.setShadowLayer(3, 0, 2, 0xff000000);

		vibro = (Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
		onLongTap = new LongTap();
		modifiers = new HashSet();
		locks = new HashSet();
		effectiveMods = new HashSet();
	}

	// Read the layout from an XML parser
	public void readLayout(XmlPullParser parser) throws XmlPullParserException, IOException {
		int i;
		String s;

		int eventType = parser.getEventType();
		if ((parser.getEventType() != XmlPullParser.START_TAG) || !parser.getName().contentEquals("Layout"))
			throw new XmlPullParserException("Expected <Layout>", parser, null);

		s = parser.getAttributeValue(null, "key_mm");
		if (s == null)
			throw new XmlPullParserException("Missing key_mm value", parser, null);
		keyMM = Float.parseFloat(s);

		parser.nextTag();

		// read the global swipes
		dir = new Action[9];
		dir[NW]  = new Action(parser);
		dir[N]   = new Action(parser);
		dir[NE]  = new Action(parser);
		dir[W]   = new Action(parser);
		dir[TAP] = new Action(parser);
		dir[E]   = new Action(parser);
		dir[SW]  = new Action(parser);
		dir[S]   = new Action(parser);
		dir[SE]  = new Action(parser);
		for (i = 0; i < 9; i++) {
			if ((dir[i] != null) && dir[i].isEmpty)
				dir[i] = null;
		}

		// drop and re-read all previously existing rows
		removeAllViews();
		while (parser.getEventType() != XmlPullParser.END_TAG) {
			if (parser.getEventType() != XmlPullParser.START_TAG)
				throw new XmlPullParserException("Expected content tag", parser, null);

			Row nr = new CompassKeyboardView.Row(getContext(), parser);
			addView(nr, lp);
		}
		if (!parser.getName().contentEquals("Layout"))
			throw new XmlPullParserException("Expected </Layout>", parser, null);
		parser.nextTag();

		columns = nKeys = 0;
		int n = getChildCount();
		for (i = 0; i < n; i++) {
			Row r = (Row)getChildAt(i);
			int nc = r.getChildCount();

			if (columns < r.columns)
				columns = r.columns;
			if (nKeys < nc)
				nKeys = nc;
		}
		//Log.d(TAG, "n="+String.valueOf(nKeys)+", cols="+String.valueOf(columns));

		// recalculate sizes and set bg colour
		calculateSizesForMetrics(getResources().getDisplayMetrics());
	}

	// Recalculate all the sizes according to the display metrics
	public void calculateSizesForMetrics(DisplayMetrics metrics) {
		// note: the metrics may change during the lifetime of the instance, so these precalculations could not be done in the constructor
		int nFontSize, keySize, maxKeySize;

		// calculate desired key size in pixels
		keySize = Math.round(keyMM * metrics.xdpi / 25.4f);
		// calculate maximal key size allowed by the metrics (2 gap pixels per key, the rest is divided between the symbol columns, key size is 3* symbol size)
		maxKeySize = (metrics.widthPixels - (2 * nKeys)) * 3 / columns;

		//Log.i(TAG, "xdpi="+String.valueOf(metrics.xdpi)+", reqKS="+String.valueOf(keySize));
		//Log.i(TAG, "w="+String.valueOf(metrics.widthPixels)+", maxKS="+String.valueOf(maxKeySize));

		// sanitise the desired key size if necessary
		if (keySize > maxKeySize)
			keySize = maxKeySize;

		// calculate gap and symbol size so that key = (gap+ sym +gap+ sym +gap+ sym +gap) = 4*gap + 3*sym, while the sum of gaps give 1/3 of the key
		gap = keySize / 12;
		nFontSize = Math.round(keySize * 2 / 9);

		// construct the Paint used for printing the labels
		textPaint.setTextSize(nFontSize);
		Paint.FontMetrics fm = textPaint.getFontMetrics();
		fontSize = fm.descent - fm.ascent;
		fontDispY = -fm.ascent;
		//Log.i(TAG, "reqFS="+String.valueOf(nFontSize)+", fs="+String.valueOf(fontSize)+", asc="+String.valueOf(fm.ascent)+", desc="+String.valueOf(fm.descent));
	
		int n = getChildCount();
		for (int i = 0; i < n; i++) {
			Row r = (Row)getChildAt(i);
			r.calculateSizes(nFontSize);
		}
		commitState();
	}

	private final class LongTap implements Runnable {
		public void run() {
			wasLongTap = true;
			processAction(dir[TAP]);
		}
	}

	boolean processGlobalSwipe(float x1, float y1, float x2, float y2) {
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
			d = NW;
		else if ((i1 == 0) && (j1 == 1) && (i2 == 0) && (j2 == -1))
			d = N;
		else if ((i1 == -1) && (j1 == 1) && (i2 == 1) && (j2 == -1))
			d = NE;
		else if ((i1 == 1) && (j1 == 0) && (i2 == -1) && (j2 == 0))
			d = W;
		else if ((i1 == -1) && (j1 == 0) && (i2 == 1) && (j2 == 0))
			d = E;
		else if ((i1 == 1) && (j1 == -1) && (i2 == -1) && (j2 == 1))
			d = SW;
		else if ((i1 == 0) && (j1 == -1) && (i2 == 0) && (j2 == 1))
			d = S;
		else if ((i1 == -1) && (j1 == -1) && (i2 == 1) && (j2 == 1))
			d = SE;
		else
			return false;

		return processAction(dir[d]);
	}

	public void setOnKeyboardActionListener(KeyboardView.OnKeyboardActionListener listener) {
		actionListener = listener;
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

		int n = getChildCount();
		for (int i = 0; i < n; i++) {
			try {
				Row r = (Row)getChildAt(i);
				r.setState(effectiveMods);
			}
			catch (ClassCastException e)
			{
				// ignoring intentionally
			}
		}
	}

	private boolean processAction(Action cd) {
		if (cd == null)
			return false;

		if (cd.mod != null) {
			changeState(cd.mod, cd.isLock);		// process a 'mod' or 'lock'
		}
		else if (cd.code != null) {
			// process a 'code'
			//Log.v(TAG, "Text: "+cd.code); 
			resetState();
			if (actionListener != null)
				actionListener.onText(cd.code);
			if (vibrateOnKey >= 0)
				vibro.vibrate(vibratePattern[vibrateOnKey], -1);
		}
		else {
			// process a 'key'
			//Log.v(TAG, "Key: "+String.valueOf(cd.keyCode)); 
			resetState();
			if (actionListener != null)
				actionListener.onKey(cd.keyCode, null);
			if (vibrateOnKey >= 0)
				vibro.vibrate(vibratePattern[vibrateOnKey], -1);
		}

		return true;
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
		//Log.d(TAG, "Toggling lock '"+name+"'");
		if (!locks.add(name))
			locks.remove(name);
		commitState();
	}

	public void changeState(String state, boolean isLock) {
		if (state == null) {
			resetState();
			if (vibrateOnCancel >= 0)
				vibro.vibrate(vibratePattern[vibrateOnCancel], -1);
		}
		else if (state.contentEquals("hide")) {
			//Log.v(TAG, "Hide");
			resetState();
			if (actionListener != null)
				actionListener.swipeDown(); // simulate hiding request

		}
		else if (isLock){
			//Log.v(TAG, "Lock: "+state); 
			resetState();
			toggleLock(state);
			if (vibrateOnModifier >= 0)
				vibro.vibrate(vibratePattern[vibrateOnModifier], -1);
		}
		else {
			//Log.v(TAG, "State: "+state); 
			addState(state);
			if (vibrateOnModifier >= 0)
				vibro.vibrate(vibratePattern[vibrateOnModifier], -1);
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
}
