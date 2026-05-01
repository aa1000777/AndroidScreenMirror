package com.example.screenmirror;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 主Activity：选择发送端或接收端角色
 * 这是应用的入口界面，用户在此选择设备角色
 */
public class MainActivity extends AppCompatActivity {

    // 发送端按钮
    private Button btnSender;
    // 接收端按钮
    private Button btnReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 设置布局文件
        setContentView(R.layout.activity_main);
        
        // 初始化视图组件
        initViews();
        
        // 设置按钮点击监听器
        setupListeners();
    }

    /**
     * 初始化视图组件
     * 获取布局中的按钮引用
     */
    private void initViews() {
        btnSender = findViewById(R.id.btn_sender);
        btnReceiver = findViewById(R.id.btn_receiver);
    }

    /**
     * 设置按钮点击监听器
     * 发送端按钮：跳转到SenderActivity
     * 接收端按钮：跳转到ReceiverActivity
     */
    private void setupListeners() {
        // 发送端按钮点击事件
        btnSender.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 创建跳转到发送端Activity的Intent
                Intent intent = new Intent(MainActivity.this, SenderActivity.class);
                // 启动发送端Activity
                startActivity(intent);
            }
        });

        // 接收端按钮点击事件
        btnReceiver.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 创建跳转到接收端Activity的Intent
                Intent intent = new Intent(MainActivity.this, ReceiverActivity.class);
                // 启动接收端Activity
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Activity恢复时，可以在此处更新UI状态
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Activity暂停时，可以在此处保存状态或释放资源
    }
}