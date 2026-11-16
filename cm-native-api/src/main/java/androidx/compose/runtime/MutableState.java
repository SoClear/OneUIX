package androidx.compose.runtime;

/** Only the inherited/read and write contracts used by the bridge. Never packaged. */
public interface MutableState<T> extends State<T> { void setValue(T value); }
