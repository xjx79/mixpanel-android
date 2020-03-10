package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.util.SparseArray;

import com.mixpanel.android.util.MPLog;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is for internal use in the Mixpanel library, and should not be imported into
 * client code.
 */
public abstract class ResourceReader implements ResourceIds {

    public static class Id extends ResourceReader {
        public Id(String resourcePackageName, Context context) {
            super(resourcePackageName, context);
            initialize();
        }

        @Override
        protected Class<?> getSystemClass() {
            return android.R.id.class;
        }

        @Override
        protected String getLocalClassName(Context context) {
            return super.mResourcePackageName + ".R$id";
        }
    }

    public static class Drawable extends ResourceReader {
        protected Drawable(String resourcePackageName, Context context) {
            super(resourcePackageName, context);
            initialize();
        }

        @Override
        protected Class<?> getSystemClass() {
            return android.R.drawable.class;
        }

        @Override
        protected String getLocalClassName(Context context) {
            return super.mResourcePackageName + ".R$drawable";
        }

    }

    public static class Mipmap extends ResourceReader {
        public Mipmap(String resourcePackageName, Context context) {
            super(resourcePackageName, context);
            initialize();
        }

        @Override
        protected Class<?> getSystemClass() {
            return android.R.mipmap.class;
        }

        @Override
        protected String getLocalClassName(Context context) {
            return super.mResourcePackageName + ".R$mipmap";
        }
    }

    protected ResourceReader(String resourcePackageName, Context context) {
        mResourcePackageName = resourcePackageName;
        mContext = context;
        mIdNameToId = new HashMap<String, Integer>();
        mIdToIdName = new SparseArray<String>();
    }

    @Override
    public boolean knownIdName(String name) {
        return mIdNameToId.containsKey(name);
    }

    @Override
    public int idFromName(String name) {
        return mIdNameToId.get(name);
    }

    @Override
    public String nameForId(int id) {
        return mIdToIdName.get(id);
    }

    private static void readClassIds(Class<?> platformIdClass, String namespace, Map<String, Integer> namesToIds) {
        try {
            final Field[] fields = platformIdClass.getFields();
            for (int i = 0; i < fields.length; i++) {
                final Field field = fields[i];
                final int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers)) {
                    final Class fieldType = field.getType();
                    if (fieldType == int.class) {
                        try {
                            final String name = field.getName();
                            final int value = field.getInt(null);
                            final String namespacedName;
                            if (null == namespace) {
                                namespacedName = name;
                            } else {
                                namespacedName = namespace + ":" + name;
                            }

                            namesToIds.put(namespacedName, value);
                        } catch (ArrayIndexOutOfBoundsException e) {
                            // https://github.com/mixpanel/mixpanel-android/issues/241
                            MPLog.e(LOGTAG, "Can't read built-in id name from " + platformIdClass.getName(), e);
                        }
                    }
                }
            }
        } catch (IllegalAccessException e) {
            MPLog.e(LOGTAG, "Can't read built-in id names from " + platformIdClass.getName(), e);
        }
    }

    protected List<String> getLocalClassNames() {
        ArrayList<String> localClassNames = new ArrayList<>();
        for (Class c : ResourceReader.class.getDeclaredClasses()) {
            localClassNames.add(mResourcePackageName + ".R$" + c.getSimpleName().toLowerCase());
        }

        return localClassNames;
    }


    protected abstract Class<?> getSystemClass();
    protected abstract String getLocalClassName(Context context);

    protected void initialize() {
        mIdNameToId.clear();
        mIdToIdName.clear();

        final Class<?> sysIdClass = getSystemClass();
        readClassIds(sysIdClass, "android", mIdNameToId);

        final List<String> localClassNames = getLocalClassNames();
        String className = getLocalClassName(mContext);
        try {
            for (String localClassName : localClassNames) {
                className = localClassName;
                final Class<?> rIdClass = Class.forName(localClassName);
                readClassIds(rIdClass, null, mIdNameToId);
            }
        } catch (ClassNotFoundException e) {
            MPLog.w(LOGTAG, "Can't load names for Android view ids from '" + className + "', ids by name will not be available in the events editor.");
            MPLog.i(LOGTAG,
                    "You may be missing a Resources class for your package due to your proguard configuration, " +
                            "or you may be using an applicationId in your build that isn't the same as the package declared in your AndroidManifest.xml file.\n" +
                            "If you're using proguard, you can fix this issue by adding the following to your proguard configuration:\n\n" +
                            "-keep class **.R$* {\n" +
                            "    <fields>;\n" +
                            "}\n\n" +
                            "If you're not using proguard, or if your proguard configuration already contains the directive above, " +
                            "you can add the following to your AndroidManifest.xml file to explicitly point the Mixpanel library to " +
                            "the appropriate library for your resources class:\n\n" +
                            "<meta-data android:name=\"com.mixpanel.android.MPConfig.ResourcePackageName\" android:value=\"YOUR_PACKAGE_NAME\" />\n\n" +
                            "where YOUR_PACKAGE_NAME is the same string you use for the \"package\" attribute in your <manifest> tag."
            );
        }

        for (Map.Entry<String, Integer> idMapping : mIdNameToId.entrySet()) {
            mIdToIdName.put(idMapping.getValue(), idMapping.getKey());
        }
    }

    private final String mResourcePackageName;
    private final Context mContext;
    private final Map<String, Integer> mIdNameToId;
    private final SparseArray<String> mIdToIdName;

    @SuppressWarnings("unused")
    private static final String LOGTAG = "MixpanelAPI.RsrcReader";

    public static final String ID_TYPE = "Ids";
    public static final String DRAWABLE_TYPE = "Drawables";
    public static final String MIPMAP_TYPE = "Mipmap";
}
