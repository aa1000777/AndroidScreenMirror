package com.screenshare.app.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.screenshare.app.R;

/**
 * 错误恢复对话框（ErrorRecoveryDialog）
 *
 * 用于显示错误信息并提供恢复选项
 */
public class ErrorRecoveryDialog extends Dialog {

    private TextView titleText;
    private TextView messageText;
    private TextView errorCodeText;
    private Button retryButton;
    private Button settingsButton;
    private Button cancelButton;

    private OnDialogActionListener actionListener;

    public interface OnDialogActionListener {
        void onRetry();
        void onOpenSettings();
        void onCancel();
    }

    public ErrorRecoveryDialog(Context context) {
        super(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_error_recovery);

        initViews();
    }

    private void initViews() {
        titleText = findViewById(R.id.dialog_title);
        messageText = findViewById(R.id.dialog_message);
        errorCodeText = findViewById(R.id.dialog_error_code);
        retryButton = findViewById(R.id.btn_retry);
        settingsButton = findViewById(R.id.btn_settings);
        cancelButton = findViewById(R.id.btn_cancel);

        retryButton.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onRetry();
            }
            dismiss();
        });

        settingsButton.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onOpenSettings();
            }
            dismiss();
        });

        cancelButton.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onCancel();
            }
            dismiss();
        });
    }

    public void setTitle(String title) {
        if (titleText != null) {
            titleText.setText(title);
        }
    }

    public void setMessage(String message) {
        if (messageText != null) {
            messageText.setText(message);
        }
    }

    public void setErrorCode(int errorCode) {
        if (errorCodeText != null) {
            errorCodeText.setText("Error code: " + errorCode);
        }
    }

    public void setOnActionListener(OnDialogActionListener listener) {
        this.actionListener = listener;
    }

    /**
     * 显示错误对话框
     */
    public static void show(Context context, String title, String message, int errorCode,
                           OnDialogActionListener listener) {
        ErrorRecoveryDialog dialog = new ErrorRecoveryDialog(context);
        dialog.setTitle(title);
        dialog.setMessage(message);
        dialog.setErrorCode(errorCode);
        dialog.setOnActionListener(listener);
        dialog.setCancelable(false);
        dialog.show();
    }

    /**
     * 显示连接断开对话框
     */
    public static void showConnectionLost(Context context, OnDialogActionListener listener) {
        show(context, "连接断开", "与投屏设备的连接已断开。是否尝试重新连接？", -1, listener);
    }

    /**
     * 显示权限被拒绝对话框
     */
    public static void showPermissionDenied(Context context, String permission, OnDialogActionListener listener) {
        show(context, "权限被拒绝", "缺少 " + permission + " 权限，无法进行投屏。", -2, listener);
    }

    /**
     * 显示编码器错误对话框
     */
    public static void showEncoderError(Context context, int errorCode, OnDialogActionListener listener) {
        show(context, "编码器错误", "视频编码出现问题。是否重试？", errorCode, listener);
    }
}