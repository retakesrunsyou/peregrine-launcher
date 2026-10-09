package net.peregrine.client.core.settings;

/** A number between min and max, shown with a slider. */
public final class SliderSetting extends Setting {

    public final float min;
    public final float max;
    public final float step;
    public final String format;  // e.g. "%.0fx" or "%.0f%%"
    private final float def;
    public float value;

    public SliderSetting(String id, String label, float min, float max, float step, float def, String format) {
        super(id, label);
        this.min = min;
        this.max = max;
        this.step = step;
        this.def = def;
        this.value = def;
        this.format = format;
    }

    public void set(float v) {
        v = Math.max(min, Math.min(max, v));
        value = step > 0 ? Math.round(v / step) * step : v;
    }

    public float fraction() {
        return (value - min) / (max - min);
    }

    public String text() {
        return String.format(format, value);
    }

    @Override
    public Object save() {
        return value;
    }

    @Override
    public void load(String v) {
        try {
            set(Float.parseFloat(v));
        } catch (NumberFormatException e) {
            // keep the default
        }
    }

    @Override
    public void reset() {
        value = def;
    }
}
