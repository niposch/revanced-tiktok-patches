package com.ss.android.ugc.aweme.feed.model;
import java.util.List;

/** Compile/test fixture only. Never included in the extension or patch bundle. */
public class Aweme {
    public boolean _isAd;
    public boolean _isSoftAd;
    public int productsCount;
    public boolean isLiveHasProduct;
    public List<?> productsInfo;
    public List<AnchorCommonStruct> anchors;
    public int getProductsCount() { return productsCount; }
    public boolean getIsLiveHasProduct() { return isLiveHasProduct; }
    public List<?> getProductsInfo() { return productsInfo; }
    public List<AnchorCommonStruct> getAnchors() { return anchors; }
}
