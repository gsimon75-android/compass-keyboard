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
import android.text.InputType;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View.MeasureSpec;
import android.view.View;
import android.view.ViewConfiguration;
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

interface ContainerItem {
	boolean processGlobalSwipe(float x1, float y1, float x2, float y2);
}

interface EmbeddableItem {
	public void calculateSizes(float symbolSize);
}

/*
 * <Action> tag
 */
class Action {
	int		keyCode, layout;
	String		code, mod, text, s;
	boolean		isLock, isEmpty;

	public Action(XmlPullParser parser) throws XmlPullParserException, IOException {
		int n = 0;

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
			else if (keyCode >= 0)
				text = 'K'+String.valueOf(keyCode);
			else 
				text = 'L'+String.valueOf(layout);
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
public class CompassKeyboardView extends LinearLayout implements ContainerItem {
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
	public static final int			FEEDBACK_HIGHLIGHT	= 1;
	public static final int			FEEDBACK_TOAST		= 2;

	// Parameters
	int					vibrateOnKey = 0;
	int					vibrateOnModifier = 0;
	int					vibrateOnCancel = 0;
	int					feedbackNormal = 0;
	int					feedbackPassword = 0;
	float					keyMM;				// maximal key size in mm-s
	float					marginLeft = 0, marginRight = 0, marginBottom = 0; // in mm-s

	// Internal params
	int					columns;	// maximal number of symbol columns (eg. 3 for full key, 2 for side key), used for size calculations
	int					nKeys;		// maximal number of keys per row, used for calculating with the gaps between keys
	float					gap;		// gap between keys in pixels
	float					fontSize;	// height of the key caption font in pixels
	float					fontDispY;	// Y-displacement of key caption font (top to baseline)
	boolean					isTypingPassword; // is the user typing a password

	Vibrator				vibro;		// vibrator
	Paint					textPaint;	// Paint for drawing key captions
	Paint					candidatePaint;	// Paint for drawing candidate key captions
	KeyboardView.OnKeyboardActionListener	actionListener;	// owner of the callback methods, result is passed to this instance
	Action[]				dir;		// global swipe <Action>s
	HashSet<String>				modifiers;	// currently active modifiers
	HashSet<String>				locks;		// currently active locks
	HashSet<String>				effectiveMods;	// currently active effective modifiers (== symmetric difference of modifiers and locks)
	LinearLayout.LayoutParams		lp;		// layout params for placing the rows

	LongTap					onLongTap;	// long tap checker
	boolean					wasLongTap;	// marker to skip processing the release of a long tap
	Toast					toast;
	float					downX, downY, upX, upY;	// the coordinates of a swipe, used for recognising global swipes

	// Common touch event handler
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

		switch (event.getAction()) {
			case MotionEvent.ACTION_UP:
				// check if global, done if it is
				if (!processGlobalSwipe(downX, downY, upX, upY))
					changeState(null, false);
		}
		return res;
	}

	/*
	 * <Align> tag
	 */
	class Align extends View implements EmbeddableItem {
		ContainerItem parent;
		int width, height;
		int xmax, ymax;

		public Align(Context context, XmlPullParser parser, ContainerItem p) throws XmlPullParserException, IOException {
			super(context);
			setBackgroundColor(0xff3f0000); // for debugging placement
			String s;

			parent = p;
			if ((parser.getEventType() != XmlPullParser.START_TAG) || !parser.getName().contentEquals("Align"))
				throw new XmlPullParserException("Expected <Align>", parser, null);

			width = height = 0;

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
		public void calculateSizes(float symbolSize) {
			xmax = Math.round(width * (symbolSize + gap));
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
	class Row extends LinearLayout implements ContainerItem, EmbeddableItem {
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
			ArrayList<State> 	state;			// the modifier <State> tags within this <Key>
			State			currentState = null;	// the currently selected <State>, according to @CompassKeyboardView::modifiers
			boolean			hasLeft, hasRight;	// does the key have the given left and right symbols?
			int			candidateDir;		// the direction into which a drag is in progress, or -1 if inactive

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

					if ((parser.getEventType() != XmlPullParser.START_TAG) || !parser.getName().contentEquals("State"))
						throw new XmlPullParserException("Expected <State>", parser, null);

					nameSet = new HashSet();
					dir = new Action[9];

					String name = parser.getAttributeValue(null, "name");
					if ((name != null) && !name.contentEquals("")) {
						String[] fields = name.split(",");
						int numFields = fields.length;
						for (i = 0; i < numFields; i++)
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

				String s;

				if ((parser.getEventType() != XmlPullParser.START_TAG) || !parser.getName().contentEquals("Key"))
					throw new XmlPullParserException("Expected <Key>", parser, null);

				hasLeft = hasRight = true;
				state = new ArrayList();

				s = parser.getAttributeValue(null, "name");
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

				candidateDir = -1;
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
					setCandidate(-1);
					invalidate();
				}
			}

			// Recalculate the drawing coordinates according to the symbol size
			public void calculateSizes(float symbolSize) {
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

			void setCandidate(int d) {
				switch (isTypingPassword ? feedbackPassword : feedbackNormal) {
					case FEEDBACK_HIGHLIGHT:
						if (candidateDir != d) {
							candidateDir = d;
							invalidate();
						}
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
				boolean res = processTouchEvent(event);
				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
					if (currentState != null)
						setCandidate(getDirection(event.getX(), event.getY()));
					return true;

					case MotionEvent.ACTION_UP:
					// check if global, done if it is
					if (processGlobalSwipe(downX, downY, upX, upY))
						return true;
					// if the key is not valid in this state or there is no corresponding Action for it, then release the modifiers
					if ((currentState == null) || !processAction(currentState.dir[getDirection(upX, upY)]))
						changeState(null, false);
					// touch event processed
					return true;

					case MotionEvent.ACTION_MOVE:
					if (currentState != null)
						setCandidate(getDirection(event.getX(), event.getY()));
				}
				return res;
			}

			// Draw a Action symbol label if it is specified
			protected void drawLabel(Canvas canvas, int d, int x, int y) {
				if (currentState.dir[d] != null) {
					if (d == candidateDir)
						canvas.drawText(currentState.dir[d].text, x, y, candidatePaint);
					else
						canvas.drawText(currentState.dir[d].text, x, y, textPaint);
				}
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
					throw new XmlPullParserException("Expected content tag", parser, null);

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
					Align na = new Align(getContext(), parser, this);

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
				EmbeddableItem e = (EmbeddableItem)getChildAt(i);
				e.calculateSizes(symbolSize);
				/*View v = getChildAt(i);
				if (v instanceof Key) 
					((Key)v).calculateSizes(symbolSize);
				else if (v instanceof Align) 
					((Align)v).calculateSizes(symbolSize);*/
			}
		}

		// Check if the swipe is a global one
		public boolean processGlobalSwipe(float x1, float y1, float x2, float y2) {
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

		candidatePaint = new Paint();
		candidatePaint.setAntiAlias(true);
		candidatePaint.setColor(Color.YELLOW);
		candidatePaint.setTextAlign(Paint.Align.CENTER);
		candidatePaint.setShadowLayer(3, 0, 2, 0xff000000);

		vibro = (Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
		onLongTap = new LongTap();
		modifiers = new HashSet();
		locks = new HashSet();
		effectiveMods = new HashSet();

		toast = Toast.makeText(context, "<none>", Toast.LENGTH_SHORT);
		toast.setGravity(Gravity.BOTTOM, 0, 0);
	}

	void vibrateCode(int n) {
		if ((n >= 0) && (n < vibratePattern.length))
			vibro.vibrate(vibratePattern[n], -1);
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
		columns = nKeys = 0;
		while (parser.getEventType() != XmlPullParser.END_TAG) {
			if (parser.getEventType() != XmlPullParser.START_TAG)
				throw new XmlPullParserException("Expected content tag", parser, null);

			if (parser.getName().contentEquals("Row")) {
				Row nr = new CompassKeyboardView.Row(getContext(), parser);
				addView(nr, lp);

				int nc = nr.getChildCount();
				if (columns < nr.columns)
					columns = nr.columns;
				if (nKeys < nc)
					nKeys = nc;
			}
			else if (parser.getName().contentEquals("Align")) {
				Align na = new Align(getContext(), parser, this);
				addView(na, lp);

				if (columns < na.width)
					columns = na.width;
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
		int nFontSize, keySize, maxKeySize;
		int marginPixelsLeft = Math.round(marginLeft * metrics.xdpi / 25.4f);
		int marginPixelsRight = Math.round(marginRight * metrics.xdpi / 25.4f);
		int marginPixelsBottom = Math.round(marginBottom * metrics.ydpi / 25.4f);
		setPadding(marginPixelsLeft, 0, marginPixelsRight, marginPixelsBottom);

		// calculate desired key size in pixels
		keySize = Math.round(keyMM * metrics.xdpi / 25.4f);
		// calculate maximal key size allowed by the metrics (2 gap pixels per key, the rest is divided between the symbol columns, key size is 3* symbol size)
		maxKeySize = ((metrics.widthPixels - marginPixelsLeft - marginPixelsRight) - (2 * nKeys)) * 3 / columns;

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
		candidatePaint.setTextSize(nFontSize * 3 / 2);
		Paint.FontMetrics fm = textPaint.getFontMetrics();
		fontSize = fm.descent - fm.ascent;
		fontDispY = -fm.ascent;
		//Log.i(TAG, "reqFS="+String.valueOf(nFontSize)+", fs="+String.valueOf(fontSize)+", asc="+String.valueOf(fm.ascent)+", desc="+String.valueOf(fm.descent));

		toast.setGravity(Gravity.TOP + Gravity.CENTER_HORIZONTAL, 0, -nFontSize);

		int n = getChildCount();
		for (int i = 0; i < n; i++) {
			EmbeddableItem e = (EmbeddableItem)getChildAt(i);
			e.calculateSizes(nFontSize);
			/*View v = getChildAt(i);
			if (v instanceof Row)
				((Row)v).calculateSizes(nFontSize);
			else if (v instanceof Align)
				((Align)v).calculateSizes(nFontSize);*/
		}
		commitState();
	}

	private final class LongTap implements Runnable {
		public void run() {
			wasLongTap = processAction(dir[TAP]);
		}
	}

	public boolean processGlobalSwipe(float x1, float y1, float x2, float y2) {
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

		int n = getChildCount();
		for (int i = 0; i < n; i++) {
			View v = getChildAt(i);
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
		else {
			if (cd.code != null) {
				// process a 'code'
				//Log.v(TAG, "Text: "+cd.code); 
				resetState();
				if (actionListener != null)
					actionListener.onText(cd.code);
			}
			else if (cd.keyCode >= 0) {
				// process a 'key'
				//Log.v(TAG, "Key: "+String.valueOf(cd.keyCode)); 
				resetState();
				if (actionListener != null)
					actionListener.onKey(cd.keyCode, null);
			}
			else if (cd.layout >= 0) {
				// process a 'layout'
				//Log.v(TAG, "Layout: "+String.valueOf(cd.layout)); 
				resetState();
				if ((actionListener != null) && (actionListener instanceof CompassKeyboard)) {
					CompassKeyboard ck = (CompassKeyboard)actionListener;
					ck.updateLayout(cd.layout);
				}
			}

			vibrateCode(vibrateOnKey);
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
			vibrateCode(vibrateOnCancel);
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
			vibrateCode(vibrateOnModifier);
		}
		else {
			//Log.v(TAG, "State: "+state); 
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
}

// vim: set ai si sw=8 ts=8 noet:

