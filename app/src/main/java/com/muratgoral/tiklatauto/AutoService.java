package com.muratgoral.tiklatauto;

import android.net.Uri;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.MediaLibraryService;
import androidx.media3.session.MediaSession;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Android Auto'ya YouTube Music gibi "medya uygulaması" olarak görünür: Favoriler, Son izlenenler, Canlı TV, Arşiv.
 * Hareket halinde de kullanılabilir çünkü yalnızca ses çalar (görüntü kanalı kapalıdır). YouTube/Vimeo/Dailymotion içerikleri listelenmez (yalnızca resmî gömme
 * oynatıcılarıyla oynatılabildikleri için burada çalınamaz).
 */
public class AutoService extends MediaLibraryService {
    private static final String ROOT = "root";
    private static final String FAV = "fav";
    private static final String HIST = "hist";
    private static final String LIVE = "live";
    private static final String ARCH = "arch";
    private static final String LIVE_GROUP = "live:";

    private MediaLibrarySession session;
    private ExoPlayer player;
    private final ExecutorService io = Executors.newFixedThreadPool(3);

    @Override
    public void onCreate() {
        super.onCreate();
        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(), true)
                .setHandleAudioBecomingNoisy(true)
                .build();
        // Yalnızca ses: görüntü kanalını indirme/çözme.
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                .build());
        session = new MediaLibrarySession.Builder(this, player, new Callback()).build();
    }

    @Override
    public MediaLibrarySession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return session;
    }

    @Override
    public void onDestroy() {
        if (session != null) {
            session.release();
            session = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        io.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ yardımcılar

    private static MediaItem folder(String id, String title) {
        return new MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle(title)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .build())
                .build();
    }

    private static MediaItem info(String title) {
        return new MediaItem.Builder()
                .setMediaId("info")
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle(title)
                        .setIsBrowsable(false)
                        .setIsPlayable(false)
                        .build())
                .build();
    }

    private static String str(JSONObject o, String key) {
        if (o == null || o.isNull(key)) return null;
        String s = o.optString(key, "");
        return s.isEmpty() ? null : s;
    }

    private static boolean playable(String type, String kind) {
        if ("channel".equals(type)) return true;
        if ("media".equals(type)) return "mp4".equals(kind) || "hls".equals(kind);
        return false;
    }

    private static MediaItem playableItem(JSONObject o) {
        String type = str(o, "type");
        String kind = str(o, "kind");
        if (type == null || !playable(type, kind)) return null;
        String prefix = "channel".equals(type) ? "ch:" : "md:";
        MediaMetadata.Builder md = new MediaMetadata.Builder()
                .setTitle(str(o, "title") == null ? "(adsız)" : str(o, "title"))
                .setSubtitle(str(o, "subtitle"))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC);
        String thumb = str(o, "thumb");
        if (thumb != null && thumb.toLowerCase(Locale.ROOT).startsWith("http")) {
            md.setArtworkUri(Uri.parse(thumb));
        }
        return new MediaItem.Builder()
                .setMediaId(prefix + o.optInt("id"))
                .setMediaMetadata(md.build())
                .build();
    }

    private List<MediaItem> itemsFrom(JSONObject resp) {
        List<MediaItem> out = new ArrayList<>();
        JSONArray arr = resp.optJSONArray("items");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            MediaItem mi = playableItem(arr.optJSONObject(i));
            if (mi != null) out.add(mi);
        }
        return out;
    }

    /** Sayfalı listeyi (en çok 'max' öğe) toplar. */
    private List<MediaItem> paged(String basePath, int max) throws Exception {
        List<MediaItem> out = new ArrayList<>();
        int page = 1;
        while (out.size() < max) {
            JSONObject r = Api.get(this, basePath + "&per=60&page=" + page);
            out.addAll(itemsFrom(r));
            if (page >= r.optInt("pages", 1)) break;
            page++;
        }
        return out;
    }

    private List<MediaItem> children(String parentId) throws Exception {
        List<MediaItem> out = new ArrayList<>();
        if (ROOT.equals(parentId)) {
            out.add(folder(FAV, "Favoriler"));
            out.add(folder(HIST, "Son izlenenler"));
            out.add(folder(LIVE, "Canlı TV"));
            out.add(folder(ARCH, "Arşiv"));
        } else if (FAV.equals(parentId)) {
            out.addAll(itemsFrom(Api.get(this, "api/favorites")));
        } else if (HIST.equals(parentId)) {
            out.addAll(itemsFrom(Api.get(this, "api/history")));
        } else if (LIVE.equals(parentId)) {
            JSONArray groups = Api.get(this, "api/meta").optJSONArray("groups");
            if (groups != null) {
                for (int i = 0; i < groups.length(); i++) {
                    JSONObject g = groups.optJSONObject(i);
                    if (g == null) continue;
                    out.add(folder(LIVE_GROUP + g.optInt("id"), String.valueOf(str(g, "name")) + " (" + g.optInt("cnt") + ")"));
                }
            }
        } else if (parentId.startsWith(LIVE_GROUP)) {
            int gid = Integer.parseInt(parentId.substring(LIVE_GROUP.length()));
            out.addAll(paged("api/items?type=channel&group=" + gid, 300));
        } else if (ARCH.equals(parentId)) {
            out.addAll(paged("api/items?type=media", 180));
        }
        if (out.isEmpty()) out.add(info("Burada çalınabilir içerik yok"));
        return out;
    }

    /** "ch:12" / "md:5" kimliğini sunucudan çalınabilir adrese çevirir. */
    private MediaItem resolve(MediaItem mi) throws Exception {
        if (mi.localConfiguration != null && mi.localConfiguration.uri != null) return mi;
        String id = mi.mediaId;
        String type;
        if (id.startsWith("ch:")) type = "channel";
        else if (id.startsWith("md:")) type = "media";
        else throw new IllegalArgumentException("Çalınamaz öğe: " + id);
        int num = Integer.parseInt(id.substring(3));
        JSONObject item = Api.get(this, "api/item?type=" + type + "&id=" + num).optJSONObject("item");
        JSONObject play = item == null ? null : item.optJSONObject("play");
        String url = play == null ? null : str(play, "url");
        if (url == null || !"stream".equals(str(play, "player"))) {
            throw new IllegalStateException("Bu içerik burada çalınamıyor.");
        }
        MediaItem.Builder b = mi.buildUpon().setUri(url);
        String lower = url.toLowerCase(Locale.ROOT);
        String path = lower.split("\\?", 2)[0];
        boolean live = "channel".equals(type);
        if (lower.contains(".m3u8") || (live && !path.matches(".*\\.[a-z0-9]{2,4}$"))) {
            b.setMimeType(MimeTypes.APPLICATION_M3U8);
        }
        return b.build();
    }

    // ------------------------------------------------------------------ oturum geri çağrıları

    private final class Callback implements MediaLibrarySession.Callback {
        @Override
        public ListenableFuture<LibraryResult<MediaItem>> onGetLibraryRoot(
                MediaLibrarySession s, MediaSession.ControllerInfo browser, LibraryParams params) {
            return Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, "Tiklat Video"), params));
        }

        @Override
        public ListenableFuture<LibraryResult<MediaItem>> onGetItem(
                MediaLibrarySession s, MediaSession.ControllerInfo browser, String mediaId) {
            return Futures.immediateFuture(LibraryResult.ofItem(folder(mediaId, mediaId), null));
        }

        @Override
        public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetChildren(
                MediaLibrarySession s, MediaSession.ControllerInfo browser, String parentId,
                int page, int pageSize, LibraryParams params) {
            SettableFuture<LibraryResult<ImmutableList<MediaItem>>> f = SettableFuture.create();
            io.execute(() -> {
                try {
                    List<MediaItem> all = children(parentId);
                    // Android Auto sayfalı isteyebilir: yalnızca istenen dilimi döndür.
                    int from = 0;
                    int to = all.size();
                    if (pageSize > 0 && pageSize < Integer.MAX_VALUE) {
                        long start = (long) Math.max(0, page) * pageSize;
                        from = (int) Math.min(start, all.size());
                        to = (int) Math.min(start + pageSize, all.size());
                    }
                    f.set(LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, to)), params));
                } catch (Api.NotLoggedIn e) {
                    ImmutableList<MediaItem> msg = ImmutableList.of(info("Telefonda Tiklat Video'yu açıp siteye giriş yapın"));
                    f.set(LibraryResult.ofItemList(msg, params));
                } catch (Exception e) {
                    ImmutableList<MediaItem> msg = ImmutableList.of(info("Yüklenemedi: " + e.getMessage()));
                    f.set(LibraryResult.ofItemList(msg, params));
                }
            });
            return f;
        }

        @Override
        public ListenableFuture<List<MediaItem>> onAddMediaItems(
                MediaSession mediaSession, MediaSession.ControllerInfo controller, List<MediaItem> mediaItems) {
            SettableFuture<List<MediaItem>> f = SettableFuture.create();
            io.execute(() -> {
                try {
                    List<MediaItem> out = new ArrayList<>();
                    for (MediaItem mi : mediaItems) out.add(resolve(mi));
                    f.set(out);
                } catch (Exception e) {
                    f.setException(e);
                }
            });
            return f;
        }
    }
}
