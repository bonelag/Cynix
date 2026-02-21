/**
 * Created specifically for FPS Free Look Camera.
 * Vùng vuốt tự do để xoay camera trong game FPS.
 * Tàng hình khi chơi game, hiện viền khi cấu hình layout.
 * Tự động nhường touch cho các nút keyboard khác nằm chồng lên.
 */

package com.limelight.binding.input.virtual_controller.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.DashPathEffect;
import android.view.MotionEvent;
import android.view.View;

import com.limelight.preferences.PreferenceConfiguration;

public class KeyBoardFreeLookArea extends keyBoardVirtualControllerElement {

    public interface FreeLookListener {
        void onMove(int deltaX, int deltaY);
    }

    private FreeLookListener listener;
    private final Paint paint = new Paint();
    private final RectF rect = new RectF();

    private long originalTouchTime = 0;
    private float lastTouchX = 0;
    private float lastTouchY = 0;

    // To accumulate fractional movement across frames
    private float remainderX = 0f;
    private float remainderY = 0f;

    private boolean isActionClick = true;

    private PreferenceConfiguration preferenceConfiguration;

    public KeyBoardFreeLookArea(KeyBoardController controller, String elementId, Context context) {
        super(controller, context, elementId);
        preferenceConfiguration = PreferenceConfiguration.readPreferences(context);
    }

    public void setListener(FreeLookListener listener) {
        this.listener = listener;
    }

    /**
     * Kiểm tra xem điểm chạm (toạ độ màn hình) có nằm trên một nút keyboard khác không.
     * Nếu có, FreeLookArea sẽ nhường touch cho nút đó.
     */
    private boolean isTouchOnSiblingButton(float screenX, float screenY) {
        for (keyBoardVirtualControllerElement element : virtualController.getElements()) {
            if (element == this) continue;
            if (element.getVisibility() != View.VISIBLE) continue;
            if (!element.enabled) continue;

            // Lấy vị trí tuyệt đối trên màn hình của element
            int[] loc = new int[2];
            element.getLocationOnScreen(loc);
            float left = loc[0];
            float top = loc[1];
            float right = left + element.getWidth();
            float bottom = top + element.getHeight();

            if (screenX >= left && screenX <= right && screenY >= top && screenY <= bottom) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        // Chỉ tàng hình khi ở chế độ chơi game (Active mode)
        if (virtualController.getControllerMode() == KeyBoardController.ControllerMode.Active) {
            canvas.drawColor(Color.TRANSPARENT);
            return;
        }

        // Trong tất cả chế độ cấu hình (MoveButtons, ResizeButtons, DisableEnableButtons):
        // Dùng hệ màu chung getDefaultColor() để có cùng màu viền với các nút khác
        int color = getDefaultColor();

        canvas.drawColor(0x15888888); // Nền rất nhạt để thấy vùng

        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(getDefaultStrokeWidth());
        paint.setPathEffect(new DashPathEffect(new float[]{20f, 10f}, 0));

        rect.left = rect.top = paint.getStrokeWidth();
        rect.right = getWidth() - rect.left;
        rect.bottom = getHeight() - rect.top;

        canvas.drawRect(rect, paint);
        
        // Vẽ text chỉ dẫn
        paint.setPathEffect(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(Math.min(getWidth(), getHeight()) * 0.08f);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(" ", getWidth() / 2f, getHeight() / 2f, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Chỉ ở chế độ Active mới kiểm tra passthrough cho các nút khác
        if (virtualController.getControllerMode() == KeyBoardController.ControllerMode.Active) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                // Kiểm tra: nếu điểm chạm nằm trên một nút keyboard khác → nhường touch
                if (isTouchOnSiblingButton(event.getRawX(), event.getRawY())) {
                    return false; // Không consume → FrameLayout sẽ dispatch cho nút đó
                }
            }
        }
        // Gọi logic mặc định của parent (xử lý Move/Resize trong config mode, 
        // hoặc gọi onElementTouchEvent trong Active mode)
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                lastTouchX = event.getRawX();
                lastTouchY = event.getRawY();
                remainderX = 0f;
                remainderY = 0f;
                originalTouchTime = event.getEventTime();
                isActionClick = true;
                return true; 
            }
            case MotionEvent.ACTION_MOVE: {
                if (listener == null) return true;

                float currentX = event.getRawX();
                float currentY = event.getRawY();

                float deltaX = currentX - lastTouchX;
                float deltaY = currentY - lastTouchY;

                // Threshold để phân biệt vuốt và chạm
                if (isActionClick && (Math.abs(deltaX) > 100 || Math.abs(deltaY) > 100)) {
                    isActionClick = false;
                }

                if (!isActionClick) {
                    // Áp dụng Sensitivity của Touchpad
                    float sensX = preferenceConfiguration.touchPadSensitivity * 0.01f;
                    float sensY = preferenceConfiguration.touchPadYSensitity * 0.01f;

                    // Tích luỹ phần thập phân (accumulation)
                    float moveX = (deltaX * sensX) + remainderX;
                    float moveY = (deltaY * sensY) + remainderY;

                    int intMoveX = (int) moveX;
                    int intMoveY = (int) moveY;

                    remainderX = moveX - intMoveX;
                    remainderY = moveY - intMoveY;

                    if (intMoveX != 0 || intMoveY != 0) {
                        listener.onMove(intMoveX, intMoveY);
                    }

                    lastTouchX = currentX;
                    lastTouchY = currentY;
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                isActionClick = true;
                return true; 
            }
        }
        return true;
    }
}
