package com.mrk.kisti;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private DevicePolicyManager devicePolicyManager;
    private ComponentName adminComponent;
    private DatabaseReference dbRef;

    private EditText etSecretCode;
    private Button btnUninstall;

    private String deviceIMEI = "123456789012345"; 

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etSecretCode = findViewById(R.id.etSecretCode);
        btnUninstall = findViewById(R.id.btnUninstall);

        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, MyAdminReceiver.class);

        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Kisti security policy activation required.");
            startActivity(intent);
        }

        FirebaseDatabase database = FirebaseDatabase.getInstance("https://mrk-kisti-default-rtdb.firebaseio.com");
        dbRef = database.getReference("devices").child(deviceIMEI);

        listenToDeviceStatus();

        btnUninstall.setOnClickListener(v -> verifyCodeAndUninstall());
    }

    private void listenToDeviceStatus() {
        dbRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    removeAdminAndUninstall();
                    return;
                }

                Boolean isLocked = snapshot.child("isLocked").getValue(Boolean.class);
                String lastDateStr = snapshot.child("lastDate").getValue(String.class);

                boolean isDateExpired = checkDateExpiry(lastDateStr);

                if ((isLocked != null && isLocked) || isDateExpired) {
                    lockPhoneNow();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this, "Sync Error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean checkDateExpiry(String lastDateStr) {
        try {
            if (lastDateStr == null) return false;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date lastDate = sdf.parse(lastDateStr);
            Date currentDate = new Date();
            
            return currentDate.after(lastDate);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void lockPhoneNow() {
        if (devicePolicyManager.isAdminActive(adminComponent)) {
            devicePolicyManager.lockNow();
        }
    }

    private void verifyCodeAndUninstall() {
        String enteredCode = etSecretCode.getText().toString().trim();

        dbRef.child("code").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String correctCode = snapshot.getValue(String.class);

                if (correctCode != null && correctCode.equals(enteredCode)) {
                    Toast.makeText(MainActivity.this, "Code Matched! Removing Protection...", Toast.LENGTH_SHORT).show();
                    removeAdminAndUninstall();
                } else {
                    Toast.makeText(MainActivity.this, "Wrong Secret Code!", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private void removeAdminAndUninstall() {
        try {
            if (devicePolicyManager.isAdminActive(adminComponent)) {
                devicePolicyManager.removeActiveAdmin(adminComponent);
            }
            Toast.makeText(this, "Device Unlocked & App Ready to Uninstall", Toast.LENGTH_LONG).show();
            finishAffinity();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
