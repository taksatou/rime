RIME
======
Rime is a programmable input method for android. You can customize
almost all behavior by writing Ruby DSL.

Example setting
-------------------

    require 'rime'

    # emacs like settings
    bind_key("C-f") { RIME::BuiltIn::right }
    bind_key("M-f") { RIME::BuiltIn::forward_sexp }
    bind_key("C-b") { RIME::BuiltIn::left }
    bind_key("M-b") { RIME::BuiltIn::backward_sexp }
    bind_key("C-v") { RIME::BuiltIn::scroll_up }
    bind_key("M-v") { RIME::BuiltIn::scroll_down }
    bind_key("C-n") { RIME::BuiltIn::next_line }
    bind_key("C-p") { RIME::BuiltIn::previous_line }


    # vi like settings
    bind_key("F1-h") { RIME::BuiltIn::left }
    bind_key("F1-j") { RIME::BuiltIn::next_line }
    bind_key("F1-k") { RIME::BuiltIn::previous_line }
    bind_key("F1-l") { RIME::BuiltIn::right }


    # You can write arbitrary ruby code:
    require 'net/http'
    require 'json'

    def get_address(postal_code)
      address = 'example.com
      path = '/path/to/something.json'

      begin
        contens = JSON.parse(Net::HTTP.get(address, path, 80))
        # do something
      rescue => e
        e.to_s
      end
    end


    # key-macro will be available in future work. :)
    bind_key("C-M-i") { RIME::BuiltIn::keymacro_rec_start }
    bind_key("C-M-o") { RIME::BuiltIn::keymacro_rec_end }
    bind_key("C-M-u") { RIME::BuiltIn::keymacro_play }


    # gestures are also customizable in future work. ;)
    bind_gesture("swipe_left") { "do something" }
    bind_gesture("swipe_right") { "do something" }
    bind_gesture("swipe_up") { "do something" }
    bind_gesture("swipe_down") { "do something" }


Build and install
-------------------
This project is built with maven-android-plugin.

    cd rime
    mvn package
    adb install target/rime-<VERSION>-apk

see also, http://code.google.com/p/maven-android-plugin/


How to use
--------------
Go to android setting > Language & input > KEYBOARD & INPUT METHODS,
and enable rime.


for eclipse users
---------------------
- http://rgladwell.github.com/m2e-android/
- https://github.com/rgladwell/m2e-android/issues/60
