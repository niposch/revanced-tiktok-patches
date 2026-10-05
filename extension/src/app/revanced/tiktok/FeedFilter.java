package app.revanced.tiktok;

import com.ss.android.ugc.aweme.feed.model.AnchorCommonStruct;
import com.ss.android.ugc.aweme.feed.model.Aweme;
import com.ss.android.ugc.aweme.feed.model.FeedItemList;
import java.util.ArrayList;
import java.util.List;
import android.util.Log;

/** Runtime code injected into TikTok; uses only members verified in 47.1.4. */
public final class FeedFilter {
    private static boolean adsActive;
    private static boolean shopActive;
    private FeedFilter() {}

    public static List<Aweme> ads(FeedItemList feed, List<Aweme> items) {
        activated(false);
        List<Aweme> filtered = filter(items, false);
        if (feed != null && feed.items == items) feed.items = filtered;
        return filtered;
    }

    public static List<Aweme> shop(FeedItemList feed, List<Aweme> items) {
        activated(true);
        List<Aweme> filtered = filter(items, true);
        if (feed != null && feed.items == items) feed.items = filtered;
        return filtered;
    }

    public static void adsFeed(FeedItemList feed) {
        if (feed == null) return;
        activated(false);
        feed.items = filter(feed.items, false);
        clearPreloadAds(feed);
    }

    public static void shopFeed(FeedItemList feed) {
        if (feed == null) return;
        activated(true);
        feed.items = filter(feed.items, true);
    }

    public static void clearPreloadAds(FeedItemList feed) {
        if (feed != null) feed.preloadAds = null;
    }

    private static List<Aweme> filter(List<Aweme> items, boolean shop) {
        if (items == null || items.isEmpty()) return items;
        // Preserve identity if clean. Copy only when removing something, so fixed-size,
        // unmodifiable and cached lists are safe and survivor order stays unchanged.
        ArrayList<Aweme> filtered = null;
        for (int i = 0; i < items.size(); i++) {
            Aweme item = items.get(i);
            boolean remove = item != null && (shop ? isShop(item) : isAd(item));
            if (remove && filtered == null) {
                filtered = new ArrayList<>(items.size());
                filtered.addAll(items.subList(0, i));
            }
            if (!remove && filtered != null) filtered.add(item);
        }
        if (filtered == null) return items;
        Log.i("TikTokFeedFilter", (shop ? "shop" : "ads") + " removed=" + (items.size() - filtered.size()));
        return filtered;
    }

    private static synchronized void activated(boolean shop) {
        if (shop ? shopActive : adsActive) return;
        if (shop) shopActive = true; else adsActive = true;
        Log.i("TikTokFeedFilter", (shop ? "shop" : "ads") + " filter active");
    }

    private static boolean isAd(Aweme item) {
        // isAd() also requires a decoded AwemeRawAd. The transport flags remain
        // authoritative even when the ad payload has not yet been materialized.
        return item._isAd || item._isSoftAd;
    }

    private static boolean isShop(Aweme item) {
        if (item.getProductsCount() > 0 || item.getIsLiveHasProduct()) return true;
        List<?> products = item.getProductsInfo();
        if (products != null && !products.isEmpty()) return true;
        List<AnchorCommonStruct> anchors = item.getAnchors();
        if (anchors == null) return false;
        for (AnchorCommonStruct anchor : anchors) {
            if (anchor == null) continue;
            switch (anchor.getType()) {
                // X/09Jg: SHOP, SHOP_LINK, SHOP_WINDOW, SHOP_MIX, SHOWCASE.
                case 3: case 6: case 33: case 35: case 89: return true;
                default: break;
            }
        }
        return false;
    }
}
