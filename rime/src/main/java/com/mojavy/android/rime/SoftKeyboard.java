/*
 * Copyright (C) 2008-2009 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.mojavy.android.pima;

import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Bundle;
import android.os.Message;
import android.os.RemoteException;
import android.text.method.MetaKeyKeyListener;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import com.mojavy.android.pima.R;
import com.mojavy.android.pima.util.PimaCallback;
import com.mojavy.android.pima.util.PimaServiceProxy;
import com.mojavy.android.pima.util.PimaUtil;
import com.mojavy.android.pima.util.Protocol;

/**
 * Example of writing an input method for a soft keyboard.  This code is
 * focused on simplicity over completeness, so it should in no way be considered
 * to be a complete soft keyboard implementation.  Its purpose is to provide
 * a basic example for how you would get started writing an input method, to
 * be fleshed out as appropriate.
 */
public class SoftKeyboard extends InputMethodService 
        implements KeyboardView.OnKeyboardActionListener {
	
	private static final int PIMA_KEYCODE_ALT = -101;
	private static final int PIMA_KEYCODE_CTRL = -102;
	private static final int PIMA_KEYCODE_F1 = -104;
	private static final int PIMA_KEYCODE_EVAL = -103;
	
	ExtractedTextRequest mTextRequest;
	Keyboard.Key mCtrl;
	Keyboard.Key mAlt;
	Keyboard.Key mF1;
	Keyboard.Key mEval;
	boolean mKeymacroRecording = false;
	boolean mKeymacroPlaying = false;
	List<Integer> mKeymacroKeyEvents = new ArrayList<Integer>();
	
    static final boolean DEBUG = false;
    
    /**
     * This boolean indicates the optional example code for performing
     * processing of hard keys in addition to regular text generation
     * from on-screen interaction.  It would be used for input methods that
     * perform language translations (such as converting text entered on 
     * a QWERTY keyboard to Chinese), but may not be used for input methods
     * that are primarily intended to be used for on-screen text entry.
     */
    static final boolean PROCESS_HARD_KEYS = true;
    
    private KeyboardView mInputView;
    private CandidateView mCandidateView;
    private CompletionInfo[] mCompletions;
    
    private StringBuilder mComposing = new StringBuilder();
    private boolean mPredictionOn;
    private boolean mCompletionOn;
    private int mLastDisplayWidth;
    private boolean mCapsLock;
    private long mLastShiftTime;
    private long mMetaState;
    
    private LatinKeyboard mSymbolsKeyboard;
    private LatinKeyboard mSymbolsShiftedKeyboard;
    private LatinKeyboard mQwertyKeyboard;
    
    private LatinKeyboard mCurKeyboard;    
    private String mWordSeparators;
    PimaServiceProxy mProxy;
    LayoutInflater mInflater = getLayoutInflater();
    
    private void showToast(String text) {
    	TextView v = new TextView(this);
    	v.setText(text);
    	v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 86);
    	Toast t = new Toast(this);
    	t.setView(v);
    	t.show();
    }
    /**
     * Main initialization of the input method component.  Be sure to call
     * to super class.
     */
    @Override public void onCreate() {
        super.onCreate();
        mWordSeparators = getResources().getString(R.string.word_separators);
        Log.i("my", "oncreate!");
        
        mProxy = new PimaServiceProxy(this);
        mProxy.bindService();  
        mTextRequest = new ExtractedTextRequest();
        mTextRequest.hintMaxChars = 655350;
        mTextRequest.hintMaxLines= 65535;
                
    }
    
    /**
     * This is the point where you can do all of your UI initialization.  It
     * is called after creation and any configuration change.
     */
    @Override public void onInitializeInterface() {
        if (mQwertyKeyboard != null) {
            // Configuration changes can happen after the keyboard gets recreated,
            // so we need to be able to re-build the keyboards if the available
            // space has changed.
            int displayWidth = getMaxWidth();
            if (displayWidth == mLastDisplayWidth) return;
            mLastDisplayWidth = displayWidth;
        }
        mQwertyKeyboard = new LatinKeyboard(this, R.xml.qwerty);
        mSymbolsKeyboard = new LatinKeyboard(this, R.xml.symbols);
        mSymbolsShiftedKeyboard = new LatinKeyboard(this, R.xml.symbols_shift);
        
        for (Keyboard.Key key: mQwertyKeyboard.getKeys()) {
        	if (key.codes[0] == PIMA_KEYCODE_ALT) {
        		mAlt = key;
        	} else if (key.codes[0] == PIMA_KEYCODE_CTRL) {
        		mCtrl = key;
        	} else if (key.codes[0] == PIMA_KEYCODE_F1) {
        		mF1 = key;
        	} else if (key.codes[0] == PIMA_KEYCODE_EVAL) {
        		mEval = key;
        	}
        }
    }
    
    /**
     * Called by the framework when your view for creating input needs to
     * be generated.  This will be called the first time your input method
     * is displayed, and every time it needs to be re-created such as due to
     * a configuration change.
     */
    @Override public View onCreateInputView() {
        mInputView = (KeyboardView) getLayoutInflater().inflate(
                R.layout.input, null);
        mInputView.setOnKeyboardActionListener(this);
        mInputView.setKeyboard(mQwertyKeyboard);
        return mInputView;
    }

    /**
     * Called by the framework when your view for showing candidates needs to
     * be generated, like {@link #onCreateInputView}.
     */
    @Override public View onCreateCandidatesView() {
        mCandidateView = new CandidateView(this);
        mCandidateView.setService(this);
        return mCandidateView;
    }

    /**
     * This is the main point where we do our initialization of the input method
     * to begin operating on an application.  At this point we have been
     * bound to the client, and are now receiving all of the detailed information
     * about the target of our edits.
     */
    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        
        // Reset our state.  We want to do this even if restarting, because
        // the underlying state of the text editor could have changed in any way.
        mComposing.setLength(0);
        updateCandidates();
        
        if (!restarting) {
            // Clear shift states.
            mMetaState = 0;
        }
        
        mPredictionOn = false;
        mCompletionOn = false;
        mCompletions = null;
        
        // We are now going to initialize our state based on the type of
        // text being edited.
        switch (attribute.inputType&EditorInfo.TYPE_MASK_CLASS) {
            case EditorInfo.TYPE_CLASS_NUMBER:
            case EditorInfo.TYPE_CLASS_DATETIME:
                // Numbers and dates default to the symbols keyboard, with
                // no extra features.
                mCurKeyboard = mSymbolsKeyboard;
                break;
                
            case EditorInfo.TYPE_CLASS_PHONE:
                // Phones will also default to the symbols keyboard, though
                // often you will want to have a dedicated phone keyboard.
                mCurKeyboard = mSymbolsKeyboard;
                break;
                
            case EditorInfo.TYPE_CLASS_TEXT:
                // This is general text editing.  We will default to the
                // normal alphabetic keyboard, and assume that we should
                // be doing predictive text (showing candidates as the
                // user types).
                mCurKeyboard = mQwertyKeyboard;
                mPredictionOn = true;
                
                // We now look for a few special variations of text that will
                // modify our behavior.
                int variation = attribute.inputType &  EditorInfo.TYPE_MASK_VARIATION;
                if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                    // Do not display predictions / what the user is typing
                    // when they are entering a password.
                    mPredictionOn = false;
                }
                
                if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS 
                        || variation == EditorInfo.TYPE_TEXT_VARIATION_URI
                        || variation == EditorInfo.TYPE_TEXT_VARIATION_FILTER) {
                    // Our predictions are not useful for e-mail addresses
                    // or URIs.
                    mPredictionOn = false;
                }
                
                if ((attribute.inputType&EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                    // If this is an auto-complete text view, then our predictions
                    // will not be shown and instead we will allow the editor
                    // to supply their own.  We only show the editor's
                    // candidates when in fullscreen mode, otherwise relying
                    // own it displaying its own UI.
                    mPredictionOn = false;
                    mCompletionOn = isFullscreenMode();
                }
                
                // We also want to look at the current state of the editor
                // to decide whether our alphabetic keyboard should start out
                // shifted.
                updateShiftKeyState(attribute);
                break;
                
            default:
                // For all unknown input types, default to the alphabetic
                // keyboard with no special features.
                mCurKeyboard = mQwertyKeyboard;
                updateShiftKeyState(attribute);
        }
        
        // Update the label on the enter key, depending on what the application
        // says it will do.
        mCurKeyboard.setImeOptions(getResources(), attribute.imeOptions);
    }

    /**
     * This is called when the user is done editing a field.  We can use
     * this to reset our state.
     */
    @Override public void onFinishInput() {
        super.onFinishInput();
        
        // Clear current composing text and candidates.
        mComposing.setLength(0);
        updateCandidates();
        
        // We only hide the candidates window when finishing input on
        // a particular editor, to avoid popping the underlying application
        // up and down if the user is entering text into the bottom of
        // its window.
        setCandidatesViewShown(false);
        
        mCurKeyboard = mQwertyKeyboard;
        if (mInputView != null) {
            mInputView.closing();
        }
    }
    
    @Override public void onStartInputView(EditorInfo attribute, boolean restarting) {
        super.onStartInputView(attribute, restarting);
        // Apply the selected keyboard to the input view.
        mInputView.setKeyboard(mCurKeyboard);
        mInputView.closing();
    }
    
    /**
     * Deal with the editor reporting movement of its cursor.
     */
    @Override public void onUpdateSelection(int oldSelStart, int oldSelEnd,
            int newSelStart, int newSelEnd,
            int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);
        
        // If the current selection in the text view changes, we should
        // clear whatever candidate text we have.
        if (mComposing.length() > 0 && (newSelStart != candidatesEnd
                || newSelEnd != candidatesEnd)) {
            mComposing.setLength(0);
            updateCandidates();
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.finishComposingText();
            }
        }
    }

    /**
     * This tells us about completions that the editor has determined based
     * on the current text in it.  We want to use this in fullscreen mode
     * to show the completions ourself, since the editor can not be seen
     * in that situation.
     */
    @Override public void onDisplayCompletions(CompletionInfo[] completions) {
        if (mCompletionOn) {
            mCompletions = completions;
            if (completions == null) {
                setSuggestions(null, false, false);
                return;
            }
            
            List<String> stringList = new ArrayList<String>();
            for (int i=0; i<(completions != null ? completions.length : 0); i++) {
                CompletionInfo ci = completions[i];
                if (ci != null) stringList.add(ci.getText().toString());
            }
            setSuggestions(stringList, true, true);
        }
    }
    
    /**
     * This translates incoming hard key events in to edit operations on an
     * InputConnection.  It is only needed when using the
     * PROCESS_HARD_KEYS option.
     */
    private boolean translateKeyDown(int keyCode, KeyEvent event) {
        mMetaState = MetaKeyKeyListener.handleKeyDown(mMetaState,
                keyCode, event);
        int c = event.getUnicodeChar(MetaKeyKeyListener.getMetaState(mMetaState));
        mMetaState = MetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState);
        InputConnection ic = getCurrentInputConnection();
        if (c == 0 || ic == null) {
            return false;
        }
        
        boolean dead = false;

        if ((c & KeyCharacterMap.COMBINING_ACCENT) != 0) {
            dead = true;
            c = c & KeyCharacterMap.COMBINING_ACCENT_MASK;
        }
        
        if (mComposing.length() > 0) {
            char accent = mComposing.charAt(mComposing.length() -1 );
            int composed = KeyEvent.getDeadChar(accent, c);

            if (composed != 0) {
                c = composed;
                mComposing.setLength(mComposing.length()-1);
            }
        }
        
        onKey(c, null);
        
        return true;
    }
    
    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                // The InputMethodService already takes care of the back
                // key for us, to dismiss the input method if it is shown.
                // However, our keyboard could be showing a pop-up window
                // that back should dismiss, so we first allow it to do that.
                if (event.getRepeatCount() == 0 && mInputView != null) {
                    if (mInputView.handleBack()) {
                        return true;
                    }
                }
                break;
                
            case KeyEvent.KEYCODE_DEL:
                // Special handling of the delete key: if we currently are
                // composing text for the user, we want to modify that instead
                // of let the application to the delete itself.
                if (mComposing.length() > 0) {
                    onKey(Keyboard.KEYCODE_DELETE, null);
                    return true;
                }
                break;
                
            case KeyEvent.KEYCODE_ENTER:
                // Let the underlying text editor always handle these.
                return false;
                
            default:
                // For all other keys, if we want to do transformations on
                // text being entered with a hard keyboard, we need to process
                // it and do the appropriate action.
                if (PROCESS_HARD_KEYS) {
                    if (keyCode == KeyEvent.KEYCODE_SPACE
                            && (event.getMetaState()&KeyEvent.META_ALT_ON) != 0) {
                        // A silly example: in our input method, Alt+Space
                        // is a shortcut for 'android' in lower case.
                        InputConnection ic = getCurrentInputConnection();
                        if (ic != null) {
                            // First, tell the editor that it is no longer in the
                            // shift state, since we are consuming this.
                            ic.clearMetaKeyStates(KeyEvent.META_ALT_ON);
                            keyDownUp(KeyEvent.KEYCODE_A);
                            keyDownUp(KeyEvent.KEYCODE_N);
                            keyDownUp(KeyEvent.KEYCODE_D);
                            keyDownUp(KeyEvent.KEYCODE_R);
                            keyDownUp(KeyEvent.KEYCODE_O);
                            keyDownUp(KeyEvent.KEYCODE_I);
                            keyDownUp(KeyEvent.KEYCODE_D);
                            // And we consume this event.
                            return true;
                        }
                    }
                    if (mPredictionOn && translateKeyDown(keyCode, event)) {
                        return true;
                    }
                }
        }
        
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        // If we want to do transformations on text being entered with a hard
        // keyboard, we need to process the up events to update the meta key
        // state we are tracking.
        if (PROCESS_HARD_KEYS) {
            if (mPredictionOn) {
                mMetaState = MetaKeyKeyListener.handleKeyUp(mMetaState,
                        keyCode, event);
            }
        }
        
        return super.onKeyUp(keyCode, event);
    }

    /**
     * Helper function to commit any text being composed in to the editor.
     */
    private void commitTyped(InputConnection inputConnection) {
        if (mComposing.length() > 0) {
        	if (mEval.on) {        	
        		handleEval(mComposing.toString());
        		mComposing.setLength(0);
        	} else {        		
	            inputConnection.commitText(mComposing, mComposing.length());
	            mComposing.setLength(0);
            	updateCandidates();
        	}
        }
    }

    /**
     * Helper to update the shift state of our keyboard based on the initial
     * editor state.
     */
    private void updateShiftKeyState(EditorInfo attr) {
        if (attr != null 
                && mInputView != null && mQwertyKeyboard == mInputView.getKeyboard()) {
            int caps = 0;
            EditorInfo ei = getCurrentInputEditorInfo();
            if (ei != null && ei.inputType != EditorInfo.TYPE_NULL) {
                caps = getCurrentInputConnection().getCursorCapsMode(attr.inputType);
            }
            mInputView.setShifted(mCapsLock || caps != 0);
        }
    }

    /**
     * Helper to determine if a given character code is alphabetic.
     */
    private boolean isAlphabet(int code) {
        if (Character.isLetter(code)) {
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * Helper to send a key down / key up pair to the current editor.
     */
    private void keyDownUp(int keyEventCode) {
        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
    }
    
    /**
     * Helper to send a character to the editor as raw key events.
     */
    private void sendKey(int keyCode) {    	
        switch (keyCode) {
            case '\n':
                keyDownUp(KeyEvent.KEYCODE_ENTER);
                break;
            default:
                if (keyCode >= '0' && keyCode <= '9') {
                    keyDownUp(keyCode - '0' + KeyEvent.KEYCODE_0);
                } else {
                    getCurrentInputConnection().commitText(String.valueOf((char) keyCode), 1);
                }
                break;
        }
    }

    // Implementation of KeyboardViewListener
    public void onKey(int primaryCode, int[] keyCodes) {    	
    	PimaUtil.info("onkey: %d", primaryCode);
    	
    	if (mEval.on) {
    		if (primaryCode != '\n' 
    				&& primaryCode != Keyboard.KEYCODE_DELETE
    				&& primaryCode != Keyboard.KEYCODE_SHIFT
    				&& primaryCode != Keyboard.KEYCODE_MODE_CHANGE
    				&& primaryCode != Keyboard.KEYCODE_CANCEL
    				&& primaryCode != LatinKeyboardView.KEYCODE_OPTIONS) {
    			if (primaryCode != PIMA_KEYCODE_EVAL
    					&& primaryCode != PIMA_KEYCODE_CTRL
    					&& primaryCode != PIMA_KEYCODE_ALT
    					&& primaryCode != PIMA_KEYCODE_F1) {
    				if (isInputViewShown()) {
    					if (mInputView.isShifted()) {
    						primaryCode = Character.toUpperCase(primaryCode);
    					}
    				}
    				mComposing.append((char) primaryCode);
    				getCurrentInputConnection().setComposingText(mComposing, 1);
    				updateShiftKeyState(getCurrentInputEditorInfo());
    				updateCandidates();
    			}
    			return;
    		}    		
    	}
        if (isWordSeparator(primaryCode)) {
            // Handle separator
            if (mComposing.length() > 0) {
                commitTyped(getCurrentInputConnection());
            }
            sendKey(primaryCode);
            updateShiftKeyState(getCurrentInputEditorInfo());
        } else if (primaryCode == PIMA_KEYCODE_ALT) {
            handlePimaAlt();
        } else if (primaryCode == PIMA_KEYCODE_CTRL) {
            handlePimaCtrl();
        } else if (primaryCode == PIMA_KEYCODE_F1) {
            handlePimaF1();
        } else if (primaryCode == PIMA_KEYCODE_EVAL) {
            handlePimaEval();
        } else if (primaryCode == Keyboard.KEYCODE_DELETE) {
            handleBackspace();
        } else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
            handleShift();
        } else if (primaryCode == Keyboard.KEYCODE_CANCEL) {
            handleClose();
            return;
        } else if (primaryCode == LatinKeyboardView.KEYCODE_OPTIONS) {
            // Show a menu or somethin'
        } else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE
                && mInputView != null) {
            Keyboard current = mInputView.getKeyboard();
            if (current == mSymbolsKeyboard || current == mSymbolsShiftedKeyboard) {
                current = mQwertyKeyboard;
            } else {
                current = mSymbolsKeyboard;
            }
            mInputView.setKeyboard(current);
            if (current == mSymbolsKeyboard) {
                current.setShifted(false);
            }
        } else {
            handleCharacter(primaryCode, keyCodes);
        }
    }
    
    PimaCallback mPimaCallback = new PimaCallback() {
    	@Override
    	public void call(Message msg) {

    		int pos = getCurrentInputEditorInfo().initialSelStart;
    		PimaUtil.info("pos: %d, id: %d", pos, msg.what);

    		switch (msg.what) {
    		case Protocol.BUILTIN_RIGHT:						
    			keyDownUp(KeyEvent.KEYCODE_DPAD_RIGHT);						
    			break;
    		case Protocol.BUILTIN_LEFT:					
    			keyDownUp(KeyEvent.KEYCODE_DPAD_LEFT);						
    			break;
    		case Protocol.BUILTIN_PREVIOUSE_LINE:						
    			keyDownUp(KeyEvent.KEYCODE_DPAD_UP);						
    			break;
    		case Protocol.BUILTIN_NEXT_LINE:						
    			keyDownUp(KeyEvent.KEYCODE_DPAD_DOWN);						
    			break;
    		case Protocol.BUILTIN_SCROLL_DOWN:			
    			keyDownUp(KeyEvent.KEYCODE_PAGE_UP);				
    			break;
    		case Protocol.BUILTIN_SCROLL_UP:
    			keyDownUp(KeyEvent.KEYCODE_PAGE_DOWN);					
    			break;						
    		case Protocol.BUILTIN_SHOW_NOTICE:
    			String res0 = msg.getData().getString(Protocol.KEY_NOTICE_MESSAGE);
    			if (res0 != null) {
    				showToast(res0);
    			} else {
    				showToast("error");
    			}
    			keyDownUp(KeyEvent.KEYCODE_DPAD_RIGHT);
//    			keyDownUp(KeyEvent.KEYCODE_DPAD_LEFT);
    			break;						
    		case Protocol.BUILTIN_RELOAD_CONFIGURATIONS:
    			String res1 = msg.getData().getString(Protocol.KEY_LOAD_FILE);
    			if (res1 != null)
    				showToast(res1); //Toast.makeText(SoftKeyboard.this, res1, Toast.LENGTH_SHORT).show();
    			else
    				showToast("error"); //Toast.makeText(SoftKeyboard.this, "error", Toast.LENGTH_SHORT).show();
    			break;					
    		case Protocol.BUILTIN_KEYMACRO_PLAY:
    			mKeymacroPlaying = true;
    			for (Integer key: mKeymacroKeyEvents) {
    				onKey(key, null);
    			}
    			break;
    		case Protocol.BUILTIN_KEYMACRO_REC_END:
    			mKeymacroRecording = false;
    			showToast("done");  //	Toast.makeText(SoftKeyboard.this, "done", Toast.LENGTH_SHORT).show();
    			break;
    		case Protocol.BUILTIN_KEYMACRO_REC_START:
    			mKeymacroKeyEvents.clear();
    			mKeymacroRecording = true;
    			showToast("recording"); //Toast.makeText(SoftKeyboard.this, "recording", Toast.LENGTH_SHORT).show();
    			break;
    		case Protocol.BUILTIN_END_OF_LINE:				
    		case Protocol.BUILTIN_BEGINNING_OF_LINE:
    		case Protocol.BUILTIN_FORWARD_SEXP:
    		case Protocol.BUILTIN_BACKWARD_SEXP:
    			PimaUtil.warn("not implemented");
    			break;
    		default:
    			if (msg.getData().getBoolean(Protocol.KEY_RESULT_STATUS)) {
    				PimaUtil.info("processed by pima service");
    				getCurrentInputConnection().commitText(msg.getData().getString(Protocol.KEY_SCRIPT_RESULT), 1);
    				mComposing.setLength(0);
    			} else {
    				PimaUtil.info("pima skipped");

    				handleCharImpl(msg.arg2);
    			}
    			break;
    		}
    	}
    };

    private void handleEval(String text) {
    	PimaUtil.info("eval: %s", text);
    	
        mProxy.putCurrentData(Protocol.KEY_CURRENT_APPLICATION, getCurrentInputEditorInfo().packageName);
        try {
			mProxy.eval(text, null, mPimaCallback);
		} catch (RemoteException e) {
			e.printStackTrace();
		}	
	}

	private void handlePimaEval() {
		// TODO Auto-generated method stub
		
	}

	private void handlePimaF1() {
		// TODO Auto-generated method stub
		
	}

	private void handlePimaAlt() {
    	PimaUtil.info("alt toggle: %d", mAlt.on ? 1 : 0);
	}

    private void handlePimaCtrl() {
    	PimaUtil.info("ctrl toggle: %d", mCtrl.on ? 1 : 0);
	}

	public void onText(CharSequence text) {
		if (mEval.on) {
			if (text != null)
				handleEval(text.toString());
			else
				PimaUtil.warn("no text");
		} else {
			InputConnection ic = getCurrentInputConnection();
			if (ic == null) return;
			ic.beginBatchEdit();
			if (mComposing.length() > 0) {
				commitTyped(ic);
			}
			ic.commitText(text, 0);
			ic.endBatchEdit();
			updateShiftKeyState(getCurrentInputEditorInfo());
		}
    }

    /**
     * Update the list of available candidates from the current composing
     * text.  This will need to be filled in by however you are determining
     * candidates.
     */
    private void updateCandidates() {
        if (!mCompletionOn) {
            if (mComposing.length() > 0) {
                ArrayList<String> list = new ArrayList<String>();
                list.add(mComposing.toString());
                setSuggestions(list, true, true);
            } else {
                setSuggestions(null, false, false);
            }
        }
    }
    
    public void setSuggestions(List<String> suggestions, boolean completions,
            boolean typedWordValid) {
        if (suggestions != null && suggestions.size() > 0) {
            setCandidatesViewShown(true);
        } else if (isExtractViewShown()) {
            setCandidatesViewShown(true);
        }
        if (mCandidateView != null) {
            mCandidateView.setSuggestions(suggestions, completions, typedWordValid);
        }
    }
    
    private void handleBackspace() {
        final int length = mComposing.length();
        if (length > 1) {
            mComposing.delete(length - 1, length);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            updateCandidates();
        } else if (length > 0) {
            mComposing.setLength(0);
            getCurrentInputConnection().commitText("", 0);
            updateCandidates();
        } else {
            keyDownUp(KeyEvent.KEYCODE_DEL);
        }
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    private void handleShift() {
        if (mInputView == null) {
            return;
        }
        
        Keyboard currentKeyboard = mInputView.getKeyboard();
        if (mQwertyKeyboard == currentKeyboard) {
            // Alphabet keyboard
            checkToggleCapsLock();
            mInputView.setShifted(mCapsLock || !mInputView.isShifted());
        } else if (currentKeyboard == mSymbolsKeyboard) {
            mSymbolsKeyboard.setShifted(true);
            mInputView.setKeyboard(mSymbolsShiftedKeyboard);
            mSymbolsShiftedKeyboard.setShifted(true);
        } else if (currentKeyboard == mSymbolsShiftedKeyboard) {
            mSymbolsShiftedKeyboard.setShifted(false);
            mInputView.setKeyboard(mSymbolsKeyboard);
            mSymbolsKeyboard.setShifted(false);
        }
    }
    
    private String toKeyExpr(int code) {
    	if (mAlt.on && mCtrl.on) {
    		return String.format("C-M-%c", (char)code);	
    	} else if (mAlt.on) {
    		return String.format("M-%c", (char)code);
    	} else if (mCtrl.on) {
    		return String.format("C-%c", (char)code);
    	} else if (mF1.on) {
    		return String.format("F1-%c", (char)code);
    	} else if (mEval.on) {
    		return String.format("%c", (char)code);
    	} else {    		
    		return String.format("%c", (char)code);
    	}    	
    }
    
    private void handleCharacter(int primaryCode, int[] keyCodes) {
        if (isInputViewShown()) {
            if (mInputView.isShifted()) {
                primaryCode = Character.toUpperCase(primaryCode);
            }
        }
        String key = toKeyExpr(primaryCode);
        CharSequence currentSelection = getCurrentInputConnection().getSelectedText(0);
        if (currentSelection != null) {
        	PimaUtil.info("selection: %s", currentSelection.toString());
        	mProxy.putCurrentData(Protocol.KEY_TEXT_SELECTION, currentSelection.toString());
        } else {
        	PimaUtil.info("no selection");
        }
        PimaUtil.info("package name: %s", getCurrentInputEditorInfo().packageName);
        
        mProxy.putCurrentData(Protocol.KEY_CURRENT_APPLICATION, getCurrentInputEditorInfo().packageName);
        try {
			mProxy.callKeyBind(key, primaryCode, mPimaCallback); 			
		} catch (RemoteException e) {
			handleCharImpl(primaryCode);
			e.printStackTrace();
			
		}
    }

    private void handleCharImpl(int code) {
    	if (isAlphabet(code) && mPredictionOn) {
    		mComposing.append((char) code);
    		getCurrentInputConnection().setComposingText(mComposing, 1);
    		updateShiftKeyState(getCurrentInputEditorInfo());
    		updateCandidates();
    	} else {
    		getCurrentInputConnection().commitText(
    				String.valueOf((char) code), 1);
    	}
    	
    }

    private void handleClose() {
        commitTyped(getCurrentInputConnection());
        requestHideSelf(0);
        mInputView.closing();
    }

    private void checkToggleCapsLock() {
        long now = System.currentTimeMillis();
        if (mLastShiftTime + 800 > now) {
            mCapsLock = !mCapsLock;
            mLastShiftTime = 0;
        } else {
            mLastShiftTime = now;
        }
    }
    
    private String getWordSeparators() {
    	if (mEval.on) {
    		return "\n";
    	} else {
    		return mWordSeparators;
    	}
    }
    
    public boolean isWordSeparator(int code) {
        String separators = getWordSeparators();
        return separators.contains(String.valueOf((char)code));
    }

    public void pickDefaultCandidate() {
        pickSuggestionManually(0);
    }
    
    public void pickSuggestionManually(int index) {
        if (mCompletionOn && mCompletions != null && index >= 0
                && index < mCompletions.length) {
            CompletionInfo ci = mCompletions[index];
            getCurrentInputConnection().commitCompletion(ci);
            if (mCandidateView != null) {
                mCandidateView.clear();
            }
            updateShiftKeyState(getCurrentInputEditorInfo());
        } else if (mComposing.length() > 0) {
            // If we were generating candidate suggestions for the current
            // text, we would commit one of them here.  But for this sample,
            // we will just commit the current text.
            commitTyped(getCurrentInputConnection());
        }
    }
    
    public void swipeRight() {
        if (mCompletionOn) {
            pickDefaultCandidate();
        }
    }
    
    public void swipeLeft() {
        handleBackspace();
    }

    public void swipeDown() {
        handleClose();
    }

    public void swipeUp() {
    }
    
    public void onPress(int primaryCode) {
    }
    
    public void onRelease(int primaryCode) {
    }
}
