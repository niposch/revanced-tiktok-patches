import app.revanced.tiktok.FeedFilter;
import com.ss.android.ugc.aweme.feed.model.*;
import com.ss.android.ugc.aweme.feed.model.live.*;
import com.ss.android.ugc.aweme.commerce.AwemeCommerceStruct;
import java.util.*;

public final class FeedFilterTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static Aweme anchor(int type) {
        Aweme item = new Aweme();
        AnchorCommonStruct link = new AnchorCommonStruct();
        link.type = type;
        item.anchors = Arrays.asList(null, link);
        return item;
    }
    private static FeedRoomTag tag(long id, String content) {
        FeedRoomTag tag = new FeedRoomTag(); tag.id = id; tag.content = content; return tag;
    }
    public static void main(String[] args) {
        Aweme normal = new Aweme(), ad = new Aweme(), softAd = new Aweme();
        ad._isAd = true;
        softAd._isSoftAd = true;
        List<Aweme> clean = Arrays.asList(normal, null, anchor(36), anchor(54), anchor(45));
        FeedItemList feed = new FeedItemList();
        feed.items = clean;
        check(FeedFilter.shop(feed, clean) == clean, "Preserve ordinary links and clean list identity");
        check(FeedFilter.ads(feed, clean) == clean, "Preserve ordinary videos");
        check(FeedFilter.ads(feed, null) == null && FeedFilter.shop(feed, null) == null, "Null list safety");
        FeedFilter.adsFeed(null);
        FeedFilter.shopFeed(null);
        FeedFilter.clearPreloadAds(null);
        List<Aweme> original = Collections.unmodifiableList(Arrays.asList(ad, normal, softAd, null));
        feed.items = original;
        List<Aweme> survivors = FeedFilter.ads(feed, original);
        check(survivors.equals(Arrays.asList(normal, null)), "Remove explicit ads, preserve order/nulls");
        check(original.size() == 4 && feed.items == survivors, "Copy immutable input and update model size");
        check(FeedFilter.ads(feed, survivors) == survivors, "Repeat filtering is idempotent");
        Aweme paid = new Aweme(), selfPromotion = new Aweme(), undisclosed = new Aweme();
        paid.commerce = new AwemeCommerceStruct(); paid.commerce.brandedContentType = 8;
        selfPromotion.commerce = new AwemeCommerceStruct(); selfPromotion.commerce.brandOrganicType = 1;
        undisclosed.commerce = new AwemeCommerceStruct(); undisclosed.commerce.brandedContentType = -1;
        List<Aweme> partnerships = Collections.unmodifiableList(Arrays.asList(normal, paid, undisclosed, selfPromotion));
        check(FeedFilter.ads(feed, partnerships).equals(Arrays.asList(normal, undisclosed)), "Remove disclosed promotions without ad flags");
        check(FeedFilter.shop(feed, partnerships) == partnerships, "Non-Shop partnerships remain independently selectable");
        check(partnerships.size() == 4, "Promotion filtering preserves immutable input");
        paid.commerce.brandedContentType = 1;
        check(FeedFilter.ads(feed, Collections.singletonList(paid)).isEmpty(), "Other positive branded types use native predicate");
        paid.commerce.brandedContentType = 0;
        check(FeedFilter.ads(feed, Collections.singletonList(paid)).size() == 1, "Commerce record alone is not an ad");
        for (int type : new int[]{3, 6, 33, 35, 89}) {
            Aweme product = anchor(type);
            List<Aweme> items = Arrays.asList(normal, product, normal);
            check(FeedFilter.shop(feed, items).equals(Arrays.asList(normal, normal)), "Shop type " + type);
            check(FeedFilter.ads(feed, items) == items, "Ads selection leaves organic Shop content");
        }
        Aweme count = new Aweme(), products = new Aweme(), live = new Aweme();
        count.productsCount = 2;
        products.productsInfo = Collections.singletonList(new Object());
        live.isLiveHasProduct = true;
        check(FeedFilter.shop(feed, Arrays.asList(count, products, live, normal)).equals(Arrays.asList(normal)), "Alternate product metadata");
        Aweme ordinaryLive = new Aweme();
        ordinaryLive.newLiveRoomData = new NewLiveRoomStruct();
        ordinaryLive.newLiveRoomData.existedCommerceGoods = true;
        ordinaryLive.newLiveRoomData.fypCommerceStruct = new FYPCommerceStruct();
        ordinaryLive.newLiveRoomData.fypCommerceStruct.commercePermission = 1L;
        ordinaryLive.newLiveRoomData.fypCommerceStruct.productNum = 0L;
        ordinaryLive.newLiveRoomData.fypCommerceStruct.popProductId = -1L;
        List<Aweme> ordinaryLives = Collections.singletonList(ordinaryLive);
        check(FeedFilter.shop(feed, ordinaryLives) == ordinaryLives, "Permission/history alone must preserve a LIVE stream");
        Aweme directLive = new Aweme(), cellLive = new Aweme(), legacyLive = new Aweme();
        directLive.newLiveRoomData = new NewLiveRoomStruct();
        directLive.newLiveRoomData.hasCommerceGoods = true;
        cellLive.roomFeedCellStruct = new RoomFeedCellStruct();
        cellLive.roomFeedCellStruct.newLiveRoomData = new NewLiveRoomStruct();
        cellLive.roomFeedCellStruct.newLiveRoomData.fypCommerceStruct = new FYPCommerceStruct();
        cellLive.roomFeedCellStruct.newLiveRoomData.fypCommerceStruct.productNum = 2L;
        legacyLive.roomFeedCellStruct = new RoomFeedCellStruct();
        legacyLive.roomFeedCellStruct.room = new LiveRoomStruct();
        legacyLive.roomFeedCellStruct.room.fypCommerceStruct = new FYPCommerceStruct();
        legacyLive.roomFeedCellStruct.room.fypCommerceStruct.popProductId = 1L;
        List<Aweme> liveCards = Collections.unmodifiableList(Arrays.asList(directLive, ordinaryLive, cellLive, null, legacyLive, normal));
        feed.items = liveCards;
        check(FeedFilter.shop(feed, liveCards).equals(Arrays.asList(ordinaryLive, null, normal)), "Filter all three LIVE room representations and retain survivor order");
        check(liveCards.size() == 6, "LIVE input is not mutated");
        check(FeedFilter.ads(feed, liveCards) == liveCards, "Shop LIVE removal remains independently selectable");
        directLive.newLiveRoomData.hasCommerceGoods = false;
        directLive.newLiveRoomData.fypCommerceStruct = new FYPCommerceStruct();
        check(FeedFilter.shop(feed, Collections.singletonList(directLive)).size() == 1, "Nullable boxed LIVE commerce fields are safe");
        cellLive.roomFeedCellStruct.newLiveRoomData = null;
        check(FeedFilter.shop(feed, Collections.singletonList(cellLive)).size() == 1, "Empty room cells remain safe");
        for (String field : new String[]{"firstTags", "subTags", "bottomTags", "bottomSubTags", "bcToggleTags", "boostToggleTags"}) {
            Aweme tagged = new Aweme(); tagged.newLiveRoomData = new NewLiveRoomStruct();
            FeedRoomTagList tags = new FeedRoomTagList(); tagged.newLiveRoomData.feedRoomTagList = tags;
            try { FeedRoomTagList.class.getField(field).set(tags, Arrays.asList(null, tag(1000001, null))); }
            catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
            check(FeedFilter.shop(feed, Arrays.asList(normal, tagged, ordinaryLive)).equals(Arrays.asList(normal, ordinaryLive)), "Commerce badge without product flags: " + field);
            check(FeedFilter.ads(feed, Collections.singletonList(tagged)).size() == 1, "Shop badge without disclosure is not an ad: " + field);
        }
        FeedRoomTagList shoppingTags = new FeedRoomTagList(); shoppingTags.bottomTags = Arrays.asList(null, tag(1000001, "Localized Shop label"));
        cellLive.roomFeedCellStruct.newLiveRoomData = new NewLiveRoomStruct(); cellLive.roomFeedCellStruct.newLiveRoomData.feedRoomTagList = shoppingTags;
        legacyLive.roomFeedCellStruct.room.hasCommerceGoods = false; legacyLive.roomFeedCellStruct.room.fypCommerceStruct = null;
        legacyLive.roomFeedCellStruct.room.feedRoomTagList = shoppingTags;
        check(FeedFilter.shop(feed, Arrays.asList(cellLive, legacyLive, ordinaryLive)).equals(Collections.singletonList(ordinaryLive)), "Shop badges work in both nested room representations");
        FeedRoomTagList recommendation = new FeedRoomTagList();
        recommendation.firstTags = Arrays.asList(null, tag(4, "Paid partnership")); recommendation.subTags = Arrays.asList(tag(1007, "Recommendation"));
        recommendation.bcToggleTags = Arrays.asList(null, tag(-1, null), tag(0, ""));
        ordinaryLive.newLiveRoomData.feedRoomTagList = recommendation;
        check(FeedFilter.shop(feed, ordinaryLives) == ordinaryLives && FeedFilter.ads(feed, ordinaryLives) == ordinaryLives, "Retain ordinary LIVE tags and empty disclosures, regardless of text");
        FeedRoomTagList disclosure = new FeedRoomTagList(); disclosure.bcToggleTags = Arrays.asList(null, tag(12, "Localized commercial disclosure"));
        directLive.newLiveRoomData.feedRoomTagList = disclosure;
        cellLive.roomFeedCellStruct.newLiveRoomData.feedRoomTagList = disclosure;
        legacyLive.roomFeedCellStruct.room.feedRoomTagList = disclosure;
        check(FeedFilter.ads(feed, Arrays.asList(directLive, ordinaryLive, cellLive, legacyLive)).equals(ordinaryLives), "Remove disclosed promotional LIVE cards in all three representations");
        check(FeedFilter.shop(feed, Collections.singletonList(directLive)).size() == 1, "Non-Shop LIVE disclosure remains independently selectable");
        Aweme shop = anchor(35);
        List<Aweme> mixed = Arrays.asList(ad, shop, normal, softAd);
        check(FeedFilter.shop(feed, FeedFilter.ads(feed, mixed)).equals(Collections.singletonList(normal)), "Combined selection");
        check(FeedFilter.ads(feed, FeedFilter.shop(feed, mixed)).equals(Collections.singletonList(normal)), "Selection order independence");
        feed.items = mixed;
        feed.preloadAds = Collections.singletonList(ad);
        FeedFilter.shopFeed(feed);
        check(feed.preloadAds != null, "Shop patch keeps separate ad selection");
        FeedFilter.adsFeed(feed);
        check(feed.items.equals(Collections.singletonList(normal)) && feed.preloadAds == null, "Feed boundary filters and clears preload");
        List<Aweme> other = Arrays.asList(shop, normal);
        List<Aweme> stored = feed.items;
        FeedFilter.shop(feed, other);
        check(feed.items == stored, "Special feed return must not replace unrelated items field");
        check(FeedFilter.ads(feed, Collections.singletonList(ad)).isEmpty(), "All filtered batch stays empty");
        System.out.println("FeedFilter behavior checks passed.");
    }
}
