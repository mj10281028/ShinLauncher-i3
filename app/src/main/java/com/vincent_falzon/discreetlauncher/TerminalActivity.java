package com.vincent_falzon.discreetlauncher ;

// License
/*

	This file is part of ShinLauncher-i3, a fork of Discreet Launcher.

	Copyright (C) 2019-2025 Vincent Falzon (Discreet Launcher)

	This program is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	This program is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */

// Imports
import android.Manifest ;
import android.content.ActivityNotFoundException ;
import android.content.Context ;
import android.content.Intent ;
import android.content.IntentFilter ;
import android.content.SharedPreferences ;
import android.content.pm.PackageManager ;
import android.graphics.Typeface ;
import android.graphics.drawable.GradientDrawable ;
import android.net.ConnectivityManager ;
import android.net.LinkProperties ;
import android.net.LinkAddress ;
import android.net.Network ;
import android.net.NetworkCapabilities ;
import android.net.Uri ;
import android.net.wifi.WifiInfo ;
import android.net.wifi.WifiManager ;
import android.os.BatteryManager ;
import android.os.Bundle ;
import android.os.Handler ;
import android.os.Looper ;
import android.provider.Settings ;
import android.text.SpannableStringBuilder ;
import android.text.Spanned ;
import android.text.TextPaint ;
import android.text.TextUtils ;
import android.text.method.LinkMovementMethod ;
import android.text.style.BackgroundColorSpan ;
import android.text.style.ClickableSpan ;
import android.text.style.ForegroundColorSpan ;
import android.view.Gravity ;
import android.view.GestureDetector ;
import android.view.KeyEvent ;
import android.view.MotionEvent ;
import android.view.View ;
import android.widget.FrameLayout ;
import android.widget.LinearLayout ;
import android.widget.ScrollView ;
import android.widget.TextView ;
import androidx.activity.OnBackPressedCallback ;
import androidx.appcompat.app.AppCompatActivity ;
import androidx.core.content.ContextCompat ;
import com.vincent_falzon.discreetlauncher.core.Application ;
import com.vincent_falzon.discreetlauncher.core.Folder ;
import com.vincent_falzon.discreetlauncher.core.Search ;
import com.vincent_falzon.discreetlauncher.events.PackagesListener ;
import com.vincent_falzon.discreetlauncher.menu.ActivityFavorites ;
import com.vincent_falzon.discreetlauncher.menu.ActivityFolders ;
import com.vincent_falzon.discreetlauncher.menu.DialogHiddenApps ;
import com.vincent_falzon.discreetlauncher.menu.DialogMenu ;
import java.net.Inet4Address ;
import java.net.InetAddress ;
import java.text.Normalizer ;
import java.text.SimpleDateFormat ;
import java.util.ArrayList ;
import java.util.Date ;
import java.util.LinkedHashMap ;
import java.util.List ;
import java.util.Locale ;
import java.util.Map ;

/**
 * Terminal home screen: i3-like top bar, 10 emulated workspaces, command line, retro keyboard.
 */
public class TerminalActivity extends AppCompatActivity
{
	// Colors
	private static final int COLOR_BG = 0xFF070B12 ;
	private static final int COLOR_CYAN = 0xFF00D4FF ;
	private static final int COLOR_TEXT = 0xFFC9D6E2 ;
	private static final int COLOR_DIM = 0xFF3A4A5A ;
	private static final int COLOR_ERROR = 0xFFFF5F56 ;
	private static final int COLOR_KEY = 0xFF0E1622 ;
	private static final int COLOR_KEY_BORDER = 0xFF16324A ;
	private static final int COLOR_LINE = 0xFF0B3A4C ;

	// Constants
	private static final String PROMPT = "shin@phone:~$ " ;
	private static final String PREFS = "shinlauncher_i3" ;
	private static final int WORKSPACES = 10 ;
	private static final int WS_SWITCH_DELAY_MS = 700 ;
	private static final int NET_REFRESH_MS = 5000 ;
	private static final int REQUEST_LOCATION = 4242 ;
	private static final String[] COMMANDS = {"help", "ls", "clear", "fav", "dossiers", "masquer", "menu", "refresh",
			"web", "wifi", "settings", "ip", "bat", "date", "loc", "ws", "close", "kb", "home", "alias", "unalias", "exit"} ;

	// Keyboard layouts
	private static final String[] ROW_NUMBERS = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"} ;
	private static final String[][] LAYOUT_QWERTZ = {
			{"q", "w", "e", "r", "t", "z", "u", "i", "o", "p"},
			{"a", "s", "d", "f", "g", "h", "j", "k", "l"},
			{"y", "x", "c", "v", "b", "n", "m"}} ;
	private static final String[][] LAYOUT_AZERTY = {
			{"a", "z", "e", "r", "t", "y", "u", "i", "o", "p"},
			{"q", "s", "d", "f", "g", "h", "j", "k", "l", "m"},
			{"w", "x", "c", "v", "b", "n"}} ;
	private static final String[][] LAYOUT_QWERTY = {
			{"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
			{"a", "s", "d", "f", "g", "h", "j", "k", "l"},
			{"z", "x", "c", "v", "b", "n", "m"}} ;
	private static final String[][] LAYOUT_SYMBOLS = {
			{"@", "#", "$", "%", "&", "*", "(", ")", "+", "="},
			{"!", "?", ":", ";", "'", "\"", "/", "\\", "|", "~"},
			{"_", "<", ">", "[", "]", "{", "}", "^"}} ;

	// Attributes
	private boolean created ;
	private SharedPreferences prefs ;
	private final Handler handler = new Handler(Looper.getMainLooper()) ;
	private Typeface typeface ;
	private float density ;
	private PackagesListener packagesListener ;
	private GestureDetector detector ;
	private boolean gestureOnKeyboard ;

	// Views
	private TextView tvWorkspaces ;
	private TextView tvNet ;
	private TextView tvSelection ;
	private TextView tvHint ;
	private LinearLayout terminal ;
	private ScrollView scroll ;
	private TextView tvOutput ;
	private TextView tvSuggest ;
	private TextView tvInput ;
	private LinearLayout keyboard ;

	// State
	private final SpannableStringBuilder output = new SpannableStringBuilder() ;
	private final StringBuilder line = new StringBuilder() ;
	private final ArrayList<String> history = new ArrayList<>() ;
	private int historyPosition ;
	private final String[] workspaces = new String[WORKSPACES + 1] ;
	private final long[] workspaceStamps = new long[WORKSPACES + 1] ;
	private int selectedWorkspace = 1 ;
	private boolean terminalVisible ;
	private boolean keyboardVisible ;
	private boolean homeTerminal ;
	private boolean shift ;
	private boolean symbols ;
	private String keyboardLayout ;
	private final LinkedHashMap<String, String> aliases = new LinkedHashMap<>() ;
	private ArrayList<String> suggestionTexts = new ArrayList<>() ;
	private ArrayList<Application> suggestionApps = new ArrayList<>() ;

	// Delayed actions
	private final Runnable workspaceLauncher = this::openSelectedWorkspace ;
	private final Runnable netRefresher = new Runnable()
		{
			@Override
			public void run()
			{
				refreshNet() ;
				handler.postDelayed(this, NET_REFRESH_MS) ;
			}
		} ;


	/**
	 * Constructor.
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState) ;

		// The applications list is built by ActivityMain: if the process was restarted, go through it first
		if(ActivityMain.getApplicationsList() == null)
			{
				Intent intent = new Intent(this, ActivityMain.class) ;
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ;
				startActivity(intent) ;
				finish() ;
				return ;
			}
		created = true ;

		// Initializations
		density = getResources().getDisplayMetrics().density ;
		prefs = getSharedPreferences(PREFS, MODE_PRIVATE) ;
		try { typeface = Typeface.createFromAsset(getAssets(), "jetbrains_mono.ttf") ; }
			catch(Exception exception) { typeface = Typeface.MONOSPACE ; }
		keyboardLayout = prefs.getString("kb_layout", "qwertz") ;
		homeTerminal = prefs.getBoolean("home_terminal", false) ;
		loadWorkspaces() ;
		loadAliases() ;

		// System bars
		getWindow().setStatusBarColor(COLOR_BG) ;
		getWindow().setNavigationBarColor(COLOR_BG) ;

		// Interface
		buildInterface() ;
		refreshWorkspacesBar() ;
		showTerminal(homeTerminal) ;
		updateInput() ;

		// Gestures
		detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener()
			{
				@Override
				public boolean onDown(MotionEvent event)
				{
					return true ;
				}

				@Override
				public boolean onDoubleTap(MotionEvent event)
				{
					openSelectedWorkspace() ;
					return true ;
				}

				@Override
				public void onLongPress(MotionEvent event)
				{
					showKeyboard(true) ;
				}

				@Override
				public boolean onFling(MotionEvent first, MotionEvent second, float velocityX, float velocityY)
				{
					if(first == null || second == null) return false ;
					float dx = second.getX() - first.getX() ;
					float dy = second.getY() - first.getY() ;
					if(Math.abs(dx) > Math.abs(dy))
						{
							if(Math.abs(dx) > dp(60) && Math.abs(velocityX) > 600)
								{
									switchWorkspace(dx < 0 ? 1 : -1) ;
									return true ;
								}
						}
						else if(dy < -dp(60) && Math.abs(velocityY) > 600)
						{
							if(!terminalVisible) showTerminal(true) ;
							return true ;
						}
						else if(dy > dp(60) && Math.abs(velocityY) > 600 && terminalVisible && !homeTerminal && scroll.getScrollY() == 0)
						{
							showTerminal(false) ;
							return true ;
						}
					return false ;
				}
			}) ;

		// Back button
		getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true)
			{
				@Override
				public void handleOnBackPressed()
				{
					if(keyboardVisible) showKeyboard(false) ;
						else if(terminalVisible && !homeTerminal) showTerminal(false) ;
				}
			}) ;

		// Listen for applications added or removed
		packagesListener = new PackagesListener() ;
		ContextCompat.registerReceiver(this, packagesListener, packagesListener.getFilter(), ContextCompat.RECEIVER_EXPORTED) ;

		// Ask for the location permission (needed by Android to read the Wi-Fi name)
		if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
				&& !prefs.getBoolean("location_asked", false))
			{
				prefs.edit().putBoolean("location_asked", true).apply() ;
				requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION) ;
			}
	}


	/**
	 * Called when the home button is pressed again.
	 */
	@Override
	protected void onNewIntent(Intent intent)
	{
		super.onNewIntent(intent) ;
		if(!created) return ;
		setIntent(intent) ;
		handler.removeCallbacks(workspaceLauncher) ;
		selectedWorkspace = 1 ;
		refreshWorkspacesBar() ;
	}


	@Override
	protected void onResume()
	{
		super.onResume() ;
		if(!created) return ;
		handler.removeCallbacks(netRefresher) ;
		handler.post(netRefresher) ;
	}


	@Override
	protected void onPause()
	{
		super.onPause() ;
		handler.removeCallbacks(netRefresher) ;
	}


	@Override
	protected void onDestroy()
	{
		handler.removeCallbacksAndMessages(null) ;
		if(packagesListener != null) unregisterReceiver(packagesListener) ;
		super.onDestroy() ;
	}


	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
	{
		super.onRequestPermissionsResult(requestCode, permissions, grantResults) ;
		if(created) refreshNet() ;
	}


	/**
	 * Feed the gesture detector before the views get the touch events.
	 */
	@Override
	public boolean dispatchTouchEvent(MotionEvent event)
	{
		if(detector != null)
		{
			if(event.getActionMasked() == MotionEvent.ACTION_DOWN)
				{
					gestureOnKeyboard = false ;
					if(keyboardVisible)
						{
							int[] location = new int[2] ;
							keyboard.getLocationOnScreen(location) ;
							gestureOnKeyboard = event.getRawY() >= location[1] ;
						}
				}
			if(!gestureOnKeyboard) detector.onTouchEvent(event) ;
		}
		return super.dispatchTouchEvent(event) ;
	}


	/**
	 * Hardware keyboard support.
	 */
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		if(created)
		{
			if(keyCode == KeyEvent.KEYCODE_ENTER)
				{
					showTerminal(true) ;
					execute(line.toString()) ;
					return true ;
				}
			if(keyCode == KeyEvent.KEYCODE_DEL)
				{
					showTerminal(true) ;
					if(line.length() > 0) line.setLength(line.length() - 1) ;
					updateInput() ;
					return true ;
				}
			int unicode = event.getUnicodeChar() ;
			if(unicode >= 32 && !event.isCtrlPressed() && !event.isAltPressed())
				{
					showTerminal(true) ;
					line.append((char)unicode) ;
					updateInput() ;
					return true ;
				}
		}
		return super.onKeyDown(keyCode, event) ;
	}


	// ---------------------------------------------------------------------------------------------
	// Interface
	// ---------------------------------------------------------------------------------------------

	private int dp(int value)
	{
		return Math.round(value * density) ;
	}


	private TextView makeText(int size_sp, int color)
	{
		TextView view = new TextView(this) ;
		view.setTypeface(typeface) ;
		view.setTextSize(size_sp) ;
		view.setTextColor(color) ;
		return view ;
	}


	private void buildInterface()
	{
		LinearLayout root = new LinearLayout(this) ;
		root.setOrientation(LinearLayout.VERTICAL) ;
		root.setBackgroundColor(COLOR_BG) ;

		// Top bar: workspaces on the left, Wi-Fi and IP on the right
		LinearLayout bar = new LinearLayout(this) ;
		bar.setOrientation(LinearLayout.HORIZONTAL) ;
		bar.setGravity(Gravity.CENTER_VERTICAL) ;
		bar.setPadding(dp(6), dp(4), dp(8), dp(4)) ;
		tvWorkspaces = makeText(11, COLOR_TEXT) ;
		tvWorkspaces.setSingleLine(true) ;
		tvNet = makeText(10, COLOR_TEXT) ;
		tvNet.setGravity(Gravity.END) ;
		tvNet.setMaxLines(2) ;
		tvNet.setEllipsize(TextUtils.TruncateAt.END) ;
		bar.addView(tvWorkspaces, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)) ;
		bar.addView(tvNet, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) ;
		root.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)) ;

		View divider = new View(this) ;
		divider.setBackgroundColor(COLOR_LINE) ;
		root.addView(divider, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))) ;

		// Selected workspace and its application
		tvSelection = makeText(11, COLOR_TEXT) ;
		tvSelection.setSingleLine(true) ;
		tvSelection.setEllipsize(TextUtils.TruncateAt.END) ;
		tvSelection.setPadding(dp(10), dp(3), dp(10), dp(3)) ;
		root.addView(tvSelection, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)) ;

		// Content: clean home hint or terminal
		FrameLayout content = new FrameLayout(this) ;
		root.addView(content, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)) ;

		tvHint = makeText(12, COLOR_DIM) ;
		tvHint.setGravity(Gravity.CENTER) ;
		tvHint.setText("↑ terminal   ·   appui long : clavier   ·   ← → : bureaux   ·   double tap : ouvrir") ;
		content.addView(tvHint, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)) ;

		terminal = new LinearLayout(this) ;
		terminal.setOrientation(LinearLayout.VERTICAL) ;
		content.addView(terminal, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)) ;

		scroll = new ScrollView(this) ;
		scroll.setFillViewport(true) ;
		tvOutput = makeText(13, COLOR_TEXT) ;
		tvOutput.setPadding(dp(10), dp(6), dp(10), dp(6)) ;
		tvOutput.setMovementMethod(LinkMovementMethod.getInstance()) ;
		tvOutput.setHighlightColor(0) ;
		scroll.addView(tvOutput, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT)) ;
		terminal.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)) ;

		tvSuggest = makeText(13, COLOR_TEXT) ;
		tvSuggest.setPadding(dp(10), dp(2), dp(10), dp(2)) ;
		tvSuggest.setMovementMethod(LinkMovementMethod.getInstance()) ;
		tvSuggest.setHighlightColor(0) ;
		terminal.addView(tvSuggest, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)) ;

		tvInput = makeText(14, COLOR_TEXT) ;
		tvInput.setPadding(dp(10), dp(6), dp(10), dp(10)) ;
		terminal.addView(tvInput, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)) ;

		// Retro keyboard (hidden until a long press)
		keyboard = new LinearLayout(this) ;
		keyboard.setOrientation(LinearLayout.VERTICAL) ;
		keyboard.setBackgroundColor(COLOR_BG) ;
		keyboard.setPadding(dp(3), dp(3), dp(3), dp(3)) ;
		keyboard.setVisibility(View.GONE) ;
		root.addView(keyboard, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)) ;
		buildKeyboard() ;

		setContentView(root) ;
	}


	private void showTerminal(boolean show)
	{
		terminalVisible = show ;
		terminal.setVisibility(show ? View.VISIBLE : View.GONE) ;
		tvHint.setVisibility(show ? View.GONE : View.VISIBLE) ;
		if(show)
			{
				if(output.length() == 0)
					{
						println("ShinLauncher-i3  —  tape 'help'", COLOR_DIM) ;
						listApplications(allApplications()) ;
					}
				scrollToBottom() ;
			}
			else if(keyboardVisible) showKeyboard(false) ;
	}


	private void showKeyboard(boolean show)
	{
		if(show && !terminalVisible) showTerminal(true) ;
		keyboardVisible = show ;
		keyboard.setVisibility(show ? View.VISIBLE : View.GONE) ;
		if(show) scrollToBottom() ;
	}


	private void scrollToBottom()
	{
		scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN)) ;
	}


	// ---------------------------------------------------------------------------------------------
	// Keyboard
	// ---------------------------------------------------------------------------------------------

	private void buildKeyboard()
	{
		keyboard.removeAllViews() ;
		String[][] letters ;
		if(keyboardLayout.equals("azerty")) letters = LAYOUT_AZERTY ;
			else if(keyboardLayout.equals("qwerty")) letters = LAYOUT_QWERTY ;
			else letters = LAYOUT_QWERTZ ;

		if(symbols)
			{
				addKeyRow(LAYOUT_SYMBOLS[0], null, null) ;
				addKeyRow(LAYOUT_SYMBOLS[1], null, null) ;
				addKeyRow(LAYOUT_SYMBOLS[2], null, "⌫") ;
			}
			else
			{
				addKeyRow(ROW_NUMBERS, null, null) ;
				addKeyRow(letters[0], null, null) ;
				addKeyRow(letters[1], null, null) ;
				addKeyRow(letters[2], "⇧", "⌫") ;
			}

		// Bottom row
		LinearLayout row = newKeyRow() ;
		addKey(row, symbols ? "abc" : "sym", 1.3f) ;
		addKey(row, "⇥", 1f) ;
		addKey(row, "↑", 1f) ;
		addKey(row, "space", 3f) ;
		addKey(row, ".", 1f) ;
		addKey(row, "-", 1f) ;
		addKey(row, "▼", 1f) ;
		addKey(row, "↵", 1.3f) ;
	}


	private LinearLayout newKeyRow()
	{
		LinearLayout row = new LinearLayout(this) ;
		row.setOrientation(LinearLayout.HORIZONTAL) ;
		keyboard.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42))) ;
		return row ;
	}


	private void addKeyRow(String[] keys, String left, String right)
	{
		LinearLayout row = newKeyRow() ;
		if(left != null) addKey(row, left, 1.5f) ;
		for(String key : keys) addKey(row, (shift && key.length() == 1) ? key.toUpperCase(Locale.ROOT) : key, 1f) ;
		if(right != null) addKey(row, right, 1.5f) ;
	}


	private void addKey(LinearLayout row, final String key, float weight)
	{
		TextView view = makeText(14, COLOR_CYAN) ;
		view.setGravity(Gravity.CENTER) ;
		view.setText(key) ;
		GradientDrawable background = new GradientDrawable() ;
		background.setColor((key.equals("⇧") && shift) ? COLOR_KEY_BORDER : COLOR_KEY) ;
		background.setStroke(dp(1), COLOR_KEY_BORDER) ;
		background.setCornerRadius(dp(3)) ;
		view.setBackground(background) ;
		view.setOnClickListener(v -> onKey(key)) ;
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight) ;
		params.setMargins(dp(2), dp(2), dp(2), dp(2)) ;
		row.addView(view, params) ;
	}


	private void onKey(String key)
	{
		switch(key)
		{
			case "⌫" :
				if(line.length() > 0) line.setLength(line.length() - 1) ;
				break ;
			case "↵" :
				execute(line.toString()) ;
				return ;
			case "⇥" :
				complete() ;
				return ;
			case "↑" :
				if(history.size() > 0)
					{
						if(historyPosition > 0) historyPosition-- ;
						line.setLength(0) ;
						line.append(history.get(historyPosition)) ;
					}
				break ;
			case "▼" :
				showKeyboard(false) ;
				return ;
			case "⇧" :
				shift = !shift ;
				buildKeyboard() ;
				return ;
			case "sym" :
			case "abc" :
				symbols = !symbols ;
				buildKeyboard() ;
				return ;
			case "space" :
				line.append(' ') ;
				break ;
			default :
				line.append(key) ;
				if(shift)
					{
						shift = false ;
						buildKeyboard() ;
					}
		}
		updateInput() ;
	}


	// ---------------------------------------------------------------------------------------------
	// Command line
	// ---------------------------------------------------------------------------------------------

	private void println(CharSequence text, int color)
	{
		int start = output.length() ;
		output.append(text) ;
		output.setSpan(new ForegroundColorSpan(color), start, output.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) ;
		output.append('\n') ;
		if(output.length() > 30000) output.delete(0, 10000) ;
		tvOutput.setText(output) ;
		scrollToBottom() ;
	}


	private void appendClickable(SpannableStringBuilder builder, String text, final int color, final Runnable action)
	{
		int start = builder.length() ;
		builder.append(text) ;
		builder.setSpan(new ClickableSpan()
			{
				@Override
				public void onClick(View view)
				{
					action.run() ;
				}

				@Override
				public void updateDrawState(TextPaint paint)
				{
					paint.setColor(color) ;
					paint.setUnderlineText(false) ;
				}
			}, start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) ;
	}


	private void updateInput()
	{
		SpannableStringBuilder builder = new SpannableStringBuilder() ;
		builder.append(PROMPT) ;
		builder.setSpan(new ForegroundColorSpan(COLOR_CYAN), 0, PROMPT.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) ;
		builder.append(line) ;
		int cursor = builder.length() ;
		builder.append('█') ;
		builder.setSpan(new ForegroundColorSpan(COLOR_CYAN), cursor, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) ;
		tvInput.setText(builder) ;
		updateSuggestions() ;
		scrollToBottom() ;
	}


	private void updateSuggestions()
	{
		suggestionTexts = new ArrayList<>() ;
		suggestionApps = new ArrayList<>() ;
		String text = line.toString().trim() ;
		SpannableStringBuilder builder = new SpannableStringBuilder() ;
		if(!text.isEmpty())
			{
				// Commands (only while typing the first word)
				if(!text.contains(" "))
					for(String command : COMMANDS)
						if(command.startsWith(text.toLowerCase(Locale.ROOT)) && suggestionTexts.size() < 3)
							{
								suggestionTexts.add(command) ;
								suggestionApps.add(null) ;
							}

				// Applications
				for(Application application : match(text))
				{
					if(suggestionTexts.size() >= 6) break ;
					suggestionTexts.add(application.getDisplayName()) ;
					suggestionApps.add(application) ;
				}
			}
		for(int i = 0 ; i < suggestionTexts.size() ; i++)
		{
			final int index = i ;
			final Application application = suggestionApps.get(i) ;
			appendClickable(builder, (i == 0 ? "▸ " : "  ") + suggestionTexts.get(i) + (application == null ? "  [cmd]" : "") + "\n",
				i == 0 ? COLOR_CYAN : COLOR_TEXT, () -> {
					if(application != null) launchApplication(application) ;
						else
						{
							line.setLength(0) ;
							line.append(suggestionTexts.get(index)).append(' ') ;
							updateInput() ;
						}
				}) ;
		}
		if(builder.length() > 0) builder.delete(builder.length() - 1, builder.length()) ;
		tvSuggest.setText(builder) ;
		tvSuggest.setVisibility(builder.length() == 0 ? View.GONE : View.VISIBLE) ;
	}


	private void complete()
	{
		if(suggestionTexts.isEmpty()) return ;
		line.setLength(0) ;
		line.append(suggestionTexts.get(0)) ;
		if(suggestionApps.get(0) == null) line.append(' ') ;
		updateInput() ;
	}


	private void execute(String raw)
	{
		String text = raw.trim() ;
		line.setLength(0) ;
		SpannableStringBuilder echo = new SpannableStringBuilder() ;
		echo.append(PROMPT).append(text) ;
		println(echo, COLOR_DIM) ;
		if(!text.isEmpty())
			{
				history.add(text) ;
				historyPosition = history.size() ;
				run(text, 0) ;
			}
		updateInput() ;
	}


	private void run(String text, int depth)
	{
		String[] parts = text.split("\\s+", 2) ;
		String command = parts[0].toLowerCase(Locale.ROOT) ;
		String argument = parts.length > 1 ? parts[1].trim() : "" ;

		// Aliases
		String alias = aliases.get(command) ;
		if(alias != null && depth < 3)
			{
				run(alias + (argument.isEmpty() ? "" : " " + argument), depth + 1) ;
				return ;
			}

		switch(command)
		{
			case "help" :
				println("<nom d'app>        lancer (↵ = 1er choix, ⇥ = compléter)\n"
					+ "ls [fav|dossiers|<dossier>]   lister les apps\n"
					+ "ws · close [N|all] bureaux (swipe ← → : choisir, double tap : ouvrir)\n"
					+ "web <mots|site>    DuckDuckGo ou ouvrir un site\n"
					+ "alias a=cmd · unalias a\n"
					+ "fav · dossiers · masquer · menu · refresh\n"
					+ "wifi · settings · ip · bat · date · loc\n"
					+ "kb qwertz|azerty|qwerty · home term|clean\n"
					+ "clear · exit", COLOR_TEXT) ;
				break ;
			case "ls" :
				commandLs(argument) ;
				break ;
			case "clear" :
				output.clear() ;
				tvOutput.setText(output) ;
				break ;
			case "exit" :
				showTerminal(false) ;
				break ;
			case "fav" :
				startActivity(new Intent(this, ActivityFavorites.class)) ;
				break ;
			case "dossiers" :
				startActivity(new Intent(this, ActivityFolders.class)) ;
				break ;
			case "masquer" :
				DialogHiddenApps.showHiddenAppsDialog(this) ;
				break ;
			case "menu" :
				new DialogMenu(this).show() ;
				break ;
			case "refresh" :
				ActivityMain.updateList(this) ;
				println("liste des apps mise à jour", COLOR_DIM) ;
				break ;
			case "web" :
				commandWeb(argument) ;
				break ;
			case "wifi" :
				startSafely(new Intent(Settings.ACTION_WIFI_SETTINGS)) ;
				break ;
			case "settings" :
				startSafely(new Intent(Settings.ACTION_SETTINGS)) ;
				break ;
			case "ip" :
				{
					String[] net = readNet() ;
					println(net[0] + "  " + net[1], COLOR_TEXT) ;
				}
				break ;
			case "bat" :
				commandBattery() ;
				break ;
			case "date" :
				println(new SimpleDateFormat("EEEE d MMMM yyyy, HH:mm:ss", Locale.getDefault()).format(new Date()), COLOR_TEXT) ;
				break ;
			case "loc" :
				if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
					println("localisation autorisée (pense à l'activer sur le téléphone)", COLOR_TEXT) ;
					else if(shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
							|| !prefs.getBoolean("location_asked", false))
						requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION) ;
					else startSafely(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))) ;
				break ;
			case "ws" :
				commandWorkspaces() ;
				break ;
			case "close" :
				commandClose(argument) ;
				break ;
			case "kb" :
				if(argument.equals("qwertz") || argument.equals("azerty") || argument.equals("qwerty"))
					{
						keyboardLayout = argument ;
						prefs.edit().putString("kb_layout", argument).apply() ;
						symbols = false ;
						buildKeyboard() ;
						println("clavier : " + argument, COLOR_TEXT) ;
					}
					else println("usage : kb qwertz|azerty|qwerty  (actuel : " + keyboardLayout + ")", COLOR_ERROR) ;
				break ;
			case "home" :
				if(argument.equals("term") || argument.equals("clean"))
					{
						homeTerminal = argument.equals("term") ;
						prefs.edit().putBoolean("home_terminal", homeTerminal).apply() ;
						println("accueil : " + (homeTerminal ? "terminal toujours ouvert" : "écran propre, swipe ↑ pour le terminal"), COLOR_TEXT) ;
					}
					else println("usage : home term|clean", COLOR_ERROR) ;
				break ;
			case "alias" :
				commandAlias(argument) ;
				break ;
			case "unalias" :
				if(aliases.remove(argument) != null)
					{
						saveAliases() ;
						println("alias supprimé : " + argument, COLOR_TEXT) ;
					}
					else println("alias inconnu : " + argument, COLOR_ERROR) ;
				break ;
			default :
				launchByName(text) ;
		}
	}


	private void startSafely(Intent intent)
	{
		try { startActivity(intent) ; }
			catch(ActivityNotFoundException exception) { println("impossible d'ouvrir cet écran", COLOR_ERROR) ; }
	}


	private void commandLs(String argument)
	{
		String name = norm(argument) ;
		if(name.isEmpty())
			{
				listApplications(allApplications()) ;
				return ;
			}
		if(name.equals("fav"))
			{
				listApplications(new ArrayList<>(ActivityMain.getApplicationsList().getFavorites())) ;
				return ;
			}
		ArrayList<Folder> folders = ActivityMain.getApplicationsList().getFolders() ;
		if(name.equals("dossiers"))
			{
				if(folders.isEmpty()) println("aucun dossier (commande : dossiers)", COLOR_DIM) ;
				for(Folder folder : folders) println(folder.getDisplayNameWithCount(), COLOR_TEXT) ;
				return ;
			}
		for(Folder folder : folders)
			if(norm(folder.getDisplayName()).startsWith(name))
				{
					listApplications(new ArrayList<>(folder.getApplications())) ;
					return ;
				}
		println("dossier introuvable : " + argument, COLOR_ERROR) ;
	}


	private void listApplications(List<Application> applications)
	{
		if(applications.isEmpty())
			{
				println("(vide)", COLOR_DIM) ;
				return ;
			}
		SpannableStringBuilder builder = new SpannableStringBuilder() ;
		for(final Application application : applications)
		{
			appendClickable(builder, application.getDisplayName(), COLOR_CYAN, () -> launchApplication(application)) ;
			builder.append("  ") ;
		}
		output.append(builder) ;
		output.append('\n') ;
		tvOutput.setText(output) ;
		scrollToBottom() ;
	}


	private void commandWeb(String argument)
	{
		if(argument.isEmpty())
			{
				println("usage : web <mots à chercher | site.ch>", COLOR_ERROR) ;
				return ;
			}
		Uri uri ;
		if(argument.startsWith("http://") || argument.startsWith("https://")) uri = Uri.parse(argument) ;
			else if(argument.matches("\\S+\\.[a-zA-Z]{2,}(/\\S*)?")) uri = Uri.parse("https://" + argument) ;
			else uri = Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(argument)) ;
		startSafely(new Intent(Intent.ACTION_VIEW, uri)) ;
	}


	private void commandBattery()
	{
		Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ;
		if(battery == null)
			{
				println("batterie : inconnue", COLOR_ERROR) ;
				return ;
			}
		int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ;
		int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ;
		int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ;
		boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL ;
		println("batterie : " + Math.round(level * 100f / scale) + " %" + (charging ? "  (en charge)" : ""), COLOR_TEXT) ;
	}


	private void commandAlias(String argument)
	{
		if(argument.isEmpty())
			{
				if(aliases.isEmpty()) println("aucun alias — exemple : alias yt=web youtube", COLOR_DIM) ;
				for(Map.Entry<String, String> entry : aliases.entrySet()) println(entry.getKey() + " = " + entry.getValue(), COLOR_TEXT) ;
				return ;
			}
		int equal = argument.indexOf('=') ;
		if(equal < 1 || equal == argument.length() - 1)
			{
				println("usage : alias nom=commande", COLOR_ERROR) ;
				return ;
			}
		String name = argument.substring(0, equal).trim().toLowerCase(Locale.ROOT) ;
		String value = argument.substring(equal + 1).trim() ;
		if(name.contains(" ") || name.isEmpty() || value.isEmpty())
			{
				println("usage : alias nom=commande (nom sans espace)", COLOR_ERROR) ;
				return ;
			}
		aliases.put(name, value) ;
		saveAliases() ;
		println("alias : " + name + " = " + value, COLOR_TEXT) ;
	}


	// ---------------------------------------------------------------------------------------------
	// Applications and workspaces
	// ---------------------------------------------------------------------------------------------

	private static String norm(String text)
	{
		return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim() ;
	}


	private ArrayList<Application> allApplications()
	{
		ArrayList<Application> result = new ArrayList<>() ;
		for(Application application : ActivityMain.getApplicationsList().getApplications(false))
		{
			if(application instanceof Search) continue ;
			if(getPackageName().equals(application.getApk())) continue ;
			result.add(application) ;
		}
		return result ;
	}


	private ArrayList<Application> match(String query)
	{
		String needle = norm(query) ;
		ArrayList<Application> exact = new ArrayList<>() ;
		ArrayList<Application> prefix = new ArrayList<>() ;
		ArrayList<Application> word = new ArrayList<>() ;
		ArrayList<Application> inside = new ArrayList<>() ;
		if(needle.isEmpty()) return exact ;
		for(Application application : allApplications())
		{
			String name = norm(application.getDisplayName()) ;
			if(name.equals(needle)) exact.add(application) ;
				else if(name.startsWith(needle)) prefix.add(application) ;
				else if(name.contains(" " + needle)) word.add(application) ;
				else if(name.contains(needle)) inside.add(application) ;
		}
		exact.addAll(prefix) ;
		exact.addAll(word) ;
		exact.addAll(inside) ;
		return exact ;
	}


	private void launchByName(String text)
	{
		ArrayList<Application> matches = match(text) ;
		if(matches.isEmpty())
			{
				println("commande ou app introuvable : " + text + "  (help)", COLOR_ERROR) ;
				return ;
			}
		launchApplication(matches.get(0)) ;
	}


	private Application findApplication(String componentInfo)
	{
		for(Application application : ActivityMain.getApplicationsList().getApplications(false))
			if(application.getComponentInfo().equals(componentInfo)) return application ;
		return null ;
	}


	private int findWorkspace(Application application)
	{
		for(int i = 2 ; i <= WORKSPACES ; i++)
			if(application.getComponentInfo().equals(workspaces[i])) return i ;
		return -1 ;
	}


	/**
	 * Start an application on its workspace (existing, the selected one if empty, or the first free one).
	 */
	private void launchApplication(Application application)
	{
		handler.removeCallbacks(workspaceLauncher) ;
		int target = findWorkspace(application) ;
		if(target < 0)
			{
				if(selectedWorkspace >= 2 && workspaces[selectedWorkspace] == null) target = selectedWorkspace ;
					else
					{
						for(int i = 2 ; i <= WORKSPACES && target < 0 ; i++)
							if(workspaces[i] == null) target = i ;
						if(target < 0)
							{
								// All workspaces are used: replace the least recently used one
								target = 2 ;
								for(int i = 3 ; i <= WORKSPACES ; i++)
									if(workspaceStamps[i] < workspaceStamps[target]) target = i ;
								println("bureaux pleins : le bureau " + target + " est remplacé", COLOR_DIM) ;
							}
					}
				workspaces[target] = application.getComponentInfo() ;
			}
		workspaceStamps[target] = System.currentTimeMillis() ;
		selectedWorkspace = target ;
		saveWorkspaces() ;
		refreshWorkspacesBar() ;
		println("→ [" + target + "] " + application.getDisplayName(), COLOR_DIM) ;
		if(!application.start(tvOutput)) println("impossible de lancer " + application.getDisplayName(), COLOR_ERROR) ;
	}


	/**
	 * Move the selection to the previous/next workspace (double tap opens its app, "close" frees it).
	 */
	private void switchWorkspace(int delta)
	{
		selectedWorkspace = ((selectedWorkspace - 1 + delta + WORKSPACES) % WORKSPACES) + 1 ;
		refreshWorkspacesBar() ;
	}


	private void openSelectedWorkspace()
	{
		if(selectedWorkspace < 2 || workspaces[selectedWorkspace] == null) return ;
		handler.removeCallbacks(workspaceLauncher) ;
		Application application = findApplication(workspaces[selectedWorkspace]) ;
		if(application == null)
			{
				println("bureau " + selectedWorkspace + " : app introuvable, bureau libéré", COLOR_ERROR) ;
				workspaces[selectedWorkspace] = null ;
				saveWorkspaces() ;
				refreshWorkspacesBar() ;
				return ;
			}
		workspaceStamps[selectedWorkspace] = System.currentTimeMillis() ;
		if(!application.start(tvOutput)) println("impossible de lancer " + application.getDisplayName(), COLOR_ERROR) ;
	}


	private void commandWorkspaces()
	{
		println("1: terminal", COLOR_TEXT) ;
		for(int i = 2 ; i <= WORKSPACES ; i++)
		{
			Application application = workspaces[i] == null ? null : findApplication(workspaces[i]) ;
			println(i + ": " + (application == null ? "—" : application.getDisplayName()), application == null ? COLOR_DIM : COLOR_TEXT) ;
		}
	}


	private void commandClose(String argument)
	{
		String text = argument.trim() ;
		if(text.equals("all"))
			{
				for(int i = 2 ; i <= WORKSPACES ; i++) workspaces[i] = null ;
				selectedWorkspace = 1 ;
				saveWorkspaces() ;
				refreshWorkspacesBar() ;
				println("tous les bureaux sont libérés", COLOR_TEXT) ;
				return ;
			}
		try
		{
			int number = text.isEmpty() ? selectedWorkspace : Integer.parseInt(text) ;
			if(number < 2 || number > WORKSPACES) throw new NumberFormatException() ;
			workspaces[number] = null ;
			saveWorkspaces() ;
			if(selectedWorkspace == number) selectedWorkspace = 1 ;
			refreshWorkspacesBar() ;
			println("bureau " + number + " libéré", COLOR_TEXT) ;
		}
		catch(NumberFormatException exception)
		{
			println("usage : close (bureau sélectionné) · close N (2 à " + WORKSPACES + ") · close all", COLOR_ERROR) ;
		}
	}


	private void refreshWorkspacesBar()
	{
		SpannableStringBuilder builder = new SpannableStringBuilder() ;
		for(int i = 1 ; i <= WORKSPACES ; i++)
		{
			int start = builder.length() ;
			builder.append(i < WORKSPACES ? " " + i + " " : "10 ") ;
			if(i == selectedWorkspace)
				{
					builder.setSpan(new BackgroundColorSpan(COLOR_CYAN), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) ;
					builder.setSpan(new ForegroundColorSpan(COLOR_BG), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) ;
				}
				else builder.setSpan(new ForegroundColorSpan((i == 1 || workspaces[i] != null) ? COLOR_CYAN : COLOR_DIM),
					start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) ;
		}
		tvWorkspaces.setText(builder) ;

		// Describe the selected workspace
		String description ;
		if(selectedWorkspace == 1) description = "bureau 1 · terminal" ;
			else if(workspaces[selectedWorkspace] == null) description = "bureau " + selectedWorkspace + " · libre" ;
			else
			{
				Application application = findApplication(workspaces[selectedWorkspace]) ;
				description = "bureau " + selectedWorkspace + " · " + (application == null ? "?" : application.getDisplayName())
					+ "   (double tap : ouvrir · close : libérer)" ;
			}
		tvSelection.setText(description) ;
	}


	private void loadWorkspaces()
	{
		String[] saved = prefs.getString("workspaces", "").split("\n", -1) ;
		for(int i = 2 ; i <= WORKSPACES ; i++)
		{
			String value = (i - 1 < saved.length) ? saved[i - 1] : "" ;
			workspaces[i] = (value.isEmpty() || value.equals("-")) ? null : value ;
		}
	}


	private void saveWorkspaces()
	{
		StringBuilder builder = new StringBuilder() ;
		for(int i = 1 ; i <= WORKSPACES ; i++)
		{
			if(i > 1) builder.append('\n') ;
			builder.append((i == 1 || workspaces[i] == null) ? "-" : workspaces[i]) ;
		}
		prefs.edit().putString("workspaces", builder.toString()).apply() ;
	}


	private void loadAliases()
	{
		aliases.clear() ;
		for(String entry : prefs.getString("aliases", "").split("\n"))
		{
			int tab = entry.indexOf('\t') ;
			if(tab > 0) aliases.put(entry.substring(0, tab), entry.substring(tab + 1)) ;
		}
	}


	private void saveAliases()
	{
		StringBuilder builder = new StringBuilder() ;
		for(Map.Entry<String, String> entry : aliases.entrySet())
			builder.append(entry.getKey()).append('\t').append(entry.getValue()).append('\n') ;
		prefs.edit().putString("aliases", builder.toString()).apply() ;
	}


	// ---------------------------------------------------------------------------------------------
	// Network
	// ---------------------------------------------------------------------------------------------

	/**
	 * Return {Wi-Fi line, IP line}.
	 */
	@SuppressWarnings("deprecation")
	private String[] readNet()
	{
		String name = "hors ligne" ;
		String address = "—" ;
		try
		{
			ConnectivityManager connectivity = (ConnectivityManager)getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE) ;
			Network network = connectivity.getActiveNetwork() ;
			if(network != null)
				{
					NetworkCapabilities capabilities = connectivity.getNetworkCapabilities(network) ;
					boolean wifi = capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ;
					name = wifi ? "wifi ?" : "mobile" ;
					LinkProperties properties = connectivity.getLinkProperties(network) ;
					if(properties != null)
						for(LinkAddress linkAddress : properties.getLinkAddresses())
						{
							InetAddress inet = linkAddress.getAddress() ;
							if(inet instanceof Inet4Address && !inet.isLoopbackAddress())
								{
									address = inet.getHostAddress() ;
									break ;
								}
						}
					if(wifi)
						{
							if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
									name = "wifi (cmd : loc)" ;
								else
								{
									WifiManager manager = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE) ;
									WifiInfo info = manager.getConnectionInfo() ;
									String ssid = info == null ? null : info.getSSID() ;
									if(ssid != null && !ssid.contains("unknown ssid"))
										name = ssid.replace("\"", "") ;
										else name = "wifi (active la localisation)" ;
								}
						}
				}
		}
		catch(Exception exception)
		{
			Utils.logError("TerminalActivity", "network info: " + exception) ;
		}
		return new String[]{name, address} ;
	}


	private void refreshNet()
	{
		String[] net = readNet() ;
		SpannableStringBuilder builder = new SpannableStringBuilder() ;
		builder.append(net[0]) ;
		builder.setSpan(new ForegroundColorSpan(COLOR_CYAN), 0, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) ;
		builder.append('\n').append(net[1]) ;
		tvNet.setText(builder) ;
	}
}
