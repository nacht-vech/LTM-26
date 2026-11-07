package shared.model;

import java.io.Serializable;

/**
 * Các loại hành động đồng bộ giữa 2 Server Active-Active.
 */
public enum SyncAction implements Serializable {
    CREATE,
    UPDATE,
    DELETE
}
