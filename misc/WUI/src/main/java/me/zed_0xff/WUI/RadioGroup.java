package me.zed_0xff.WUI;

import java.util.ArrayList;
import java.util.List;

final class RadioGroup {
    private final List<RadioButton> buttons = new ArrayList<>();

    void add(RadioButton rb) { buttons.add(rb); }

    void select(RadioButton chosen) {
        buttons.forEach(r -> r.checked = (r == chosen));
    }

    RadioButton getChecked() {
        return buttons.stream().filter(r -> r.checked).findFirst().orElse(null);
    }
}
