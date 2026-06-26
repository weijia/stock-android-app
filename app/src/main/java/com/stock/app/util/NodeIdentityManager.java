package com.stock.app.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

/**
 * 节点身份管理器
 * 负责生成和维护客户端唯一标识（UUID）
 *
 * 设计：
 * - 首次启动时生成 UUID，保存到 SharedPreferences
 * - 后续启动读取已有 UUID
 * - UUID 随应用生命周期：卸载重装 = 全新节点
 */
public class NodeIdentityManager {
    private static final String PREF_NAME = "stock_node_identity";
    private static final String KEY_NODE_ID = "node_id";

    private SharedPreferences prefs;

    public NodeIdentityManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 获取节点 ID
     * 如果不存在则生成新的 UUID
     */
    public String getNodeId() {
        String nodeId = prefs.getString(KEY_NODE_ID, null);
        if (nodeId == null || nodeId.isEmpty()) {
            nodeId = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_NODE_ID, nodeId).apply();
        }
        return nodeId;
    }

    /**
     * 检查是否为新节点（首次生成 ID）
     */
    public boolean isNewNode() {
        return !prefs.contains(KEY_NODE_ID);
    }

    /**
     * 重置节点 ID（调试用）
     */
    public void resetNodeId() {
        prefs.edit().remove(KEY_NODE_ID).apply();
    }
}