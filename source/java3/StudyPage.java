package today.knowingjesus.biblecorner;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/** My Study: bookmarks, highlights, notes and Study Pads; and the My KJT account, signed in here in the app. */
final class StudyPage implements Main.Page {
    final Main m;
    final LinearLayout root, box;
    String filter = "";

    StudyPage(Main m) {
        this.m = m;
        box = new LinearLayout(m); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(0, K.dp(6), 0, K.dp(30));
        root = m.scrollPage(m.bar(m.barTitle("My Study"),
            m.barButton("ic_refresh", "Bring in step with My KJT now", new View.OnClickListener() { public void onClick(View v) { sync(); } })), box);
        Study.listeners.add(new Study.Listener() { public void changed() { if (root.isAttachedToWindow()) show(); } });
    }

    public View view() { return root; }
    public boolean back() { if (filter.length() > 0) { filter = ""; show(); return true; } return false; }

    TextView heading(String s) { TextView t = K.text(m, s, 14, K.brand(), true); t.setPadding(K.dp(16), K.dp(16), K.dp(16), K.dp(6)); box.addView(t); return t; }

    public void show() {
        box.removeAllViews();
        account();
        List<JSONObject> recs = Db.get().records();
        List<JSONObject> pads = Db.get().pads();
        if (!pads.isEmpty()) {
            heading("STUDY PADS");
            for (final JSONObject p : pads) {
                final String tag = p.optString("tag"); int n = 0;
                for (JSONObject r : recs) if (Study.tags(r).contains(tag)) n++;
                box.addView(K.row(m, (tag.equals(filter) ? "✓ " : "") + "#" + tag, n + " verses", new View.OnClickListener() { public void onClick(View v) { filter = tag.equals(filter) ? "" : tag; show(); } }));
            }
        }
        heading(filter.length() > 0 ? "#" + filter : "BOOKMARKS AND NOTES");
        List<JSONObject> shown = new ArrayList<>();
        for (JSONObject r : recs) if (filter.isEmpty() || Study.tags(r).contains(filter)) shown.add(r);
        Collections.sort(shown, new Comparator<JSONObject>() { public int compare(JSONObject a, JSONObject b) { return Long.compare(b.optLong("updated", b.optLong("created")), a.optLong("updated", a.optLong("created"))); } });
        if (recs.isEmpty()) { TextView t = K.text(m, "Nothing yet. In the Holy Bible, tap a verse, then bookmark it, give it a colour or write a note.", 15, K.dim(), false); t.setPadding(K.dp(16), 0, K.dp(16), 0); box.addView(t); }
        int count = 0;
        for (final JSONObject r : shown) {
            if (++count > 400) break;
            String tr = r.optString("translation"), book = r.optString("book"); int ch = r.optInt("chapter"), v = Math.max(1, r.optInt("verse")), ve = r.optInt("verse_end");
            StringBuilder txt = new StringBuilder();
            for (Object[] x : Db.get().passage(tr, book, ch, v, ch, ve > v ? ve : v)) { if (txt.length() > 0) txt.append(' '); txt.append(K.strip((String) x[2])); }
            String note = r.optString("note").trim();
            String ref = K.bookName(book, tr) + " " + ch + ":" + v + (ve > v ? "–" + ve : "");
            List<String> tags = Study.tags(r); StringBuilder tg = new StringBuilder(); for (String t : tags) tg.append(" #").append(t);
            LinearLayout row = K.row(m, ref + tg, note.length() > 0 ? "✎ " + note : txt.toString(), new View.OnClickListener() { public void onClick(View x) { m.openPassage(r.optString("book"), r.optInt("chapter"), Math.max(1, r.optInt("verse")), r.optString("translation")); } });
            if (r.has("color")) { View dot = new View(m); dot.setBackground(K.round(K.color(r.optString("color")), 5)); row.addView(dot, 0, new LinearLayout.LayoutParams(K.dp(28), K.dp(5))); }
            row.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View x) { remove(r); return true; } });
            box.addView(row); box.addView(K.divider(m));
        }
        if (!recs.isEmpty()) { TextView t = K.text(m, "Touch and hold a line to remove it.", 13, K.dim(), false); t.setPadding(K.dp(16), K.dp(10), K.dp(16), 0); box.addView(t); }
    }

    void remove(final JSONObject r) {
        new AlertDialog.Builder(m).setTitle("Remove this?").setMessage(K.bookName(r.optString("book"), r.optString("translation")) + " " + r.optInt("chapter") + ":" + r.optInt("verse"))
            .setPositiveButton("Remove", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { Study.delete(r); } })
            .setNegativeButton("Cancel", null).show();
    }

    // ---------- the account ----------
    void account() {
        JSONObject u = Study.user();
        LinearLayout c = new LinearLayout(m); c.setOrientation(LinearLayout.VERTICAL); c.setBackground(K.round(K.card(), 14)); c.setElevation(K.dp(1.5f));
        c.setPadding(K.dp(16), K.dp(14), K.dp(16), K.dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(K.dp(12), K.dp(6), K.dp(12), K.dp(4));
        box.addView(c, lp);
        if (u != null) {
            c.addView(K.text(m, u.optString("name", "Signed in"), 18, K.brand(), true));
            String when = Db.get().kv("synced");
            String sub = "Kept in step with My KJT";
            try { if (when != null) sub += " · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(Long.parseLong(when) * 1000)); } catch (Throwable t) {}
            c.addView(K.text(m, sub, 14, K.dim(), false));
            int waiting = Db.get().outbox().size();
            if (waiting > 0) c.addView(K.text(m, waiting + " changes wait for the internet; they go by themselves.", 13, K.dim(), false));
            android.widget.Button out = K.button(m, "Sign out", false);
            out.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { signOut(); } });
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2); bp.setMargins(0, K.dp(10), 0, 0); bp.gravity = Gravity.END;
            c.addView(out, bp);
        } else {
            c.addView(K.text(m, "My KJT account", 18, K.brand(), true));
            c.addView(K.text(m, "Your bookmarks and notes stay on this phone. Sign in to keep them in step with My KJT on the website and your computer.", 14, K.dim(), false));
            android.widget.Button in = K.button(m, "Sign in", true);
            in.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { signIn(null); } });
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2); bp.setMargins(0, K.dp(10), 0, 0); bp.gravity = Gravity.END;
            c.addView(in, bp);
        }
    }

    void signIn(String error) {
        LinearLayout f = new LinearLayout(m); f.setOrientation(LinearLayout.VERTICAL); f.setPadding(K.dp(22), K.dp(6), K.dp(22), 0);
        if (error != null) { TextView e = K.text(m, error, 14, 0xffc0392b, true); f.addView(e); }
        final EditText login = new EditText(m); login.setHint("Name or e-mail"); login.setSingleLine(true); login.setText(K.get("last_login", ""));
        login.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        final EditText pw = new EditText(m); pw.setHint("Password"); pw.setSingleLine(true); pw.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        f.addView(login); f.addView(pw);
        TextView forgot = K.text(m, "Forgot your password?", 14, K.accent(), false); forgot.setPadding(0, K.dp(10), 0, K.dp(4));
        forgot.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { m.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Net.site() + "/wp-login.php?action=lostpassword"))); } });
        TextView reg = K.text(m, "No account yet? Make one on the website", 14, K.accent(), false); reg.setPadding(0, K.dp(6), 0, K.dp(4));
        reg.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { m.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Net.site() + "/wp-login.php?action=register"))); } });
        f.addView(forgot); f.addView(reg);
        new AlertDialog.Builder(m).setTitle("Sign in to My KJT").setView(f)
            .setPositiveButton("Sign in", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                String l = login.getText().toString().trim(), p = pw.getText().toString();
                if (l.isEmpty() || p.isEmpty()) { signIn("Please write your name (or e-mail) and password."); return; }
                if (!Net.online()) { K.toast("Signing in needs the internet"); return; }
                K.put("last_login", l);
                K.toast("Signing in…");
                Study.signIn(l, p, new Study.Done() { public void done(String r) {
                    if ("ok".equals(r)) { JSONObject u = Study.user(); K.toast("Welcome" + (u != null ? ", " + u.optString("name") : "") + ". Your study is kept in step with My KJT."); show(); }
                    else signIn("wrong".equals(r) ? "The name or the password is not right." : "limit".equals(r) ? "Too many tries. Please wait a few minutes." : "net".equals(r) ? "The website could not be reached." : "Signing in did not work. Please try again.");
                } });
            } })
            .setNegativeButton("Cancel", null).show();
    }

    void signOut() {
        new AlertDialog.Builder(m).setTitle("Sign out?").setMessage("Your study stays safe in My KJT on the website. This phone forgets it until you sign in again.")
            .setPositiveButton("Sign out", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                K.bg.execute(new Runnable() { public void run() { Study.flush(); Study.signOut(new Runnable() { public void run() { K.toast("Signed out"); show(); } }); } });
            } })
            .setNegativeButton("Cancel", null).show();
    }

    void sync() {
        if (Study.user() == null) { signIn(null); return; }
        K.toast("Bringing in step…");
        Study.sync(new Study.Done() { public void done(String r) { K.toast("ok".equals(r) ? "In step with My KJT" : "guest".equals(r) ? "Not signed in" : "The website could not be reached"); show(); } });
    }
}
