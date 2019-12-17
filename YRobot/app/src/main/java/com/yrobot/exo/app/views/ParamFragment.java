package com.yrobot.exo.app.views;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.xw.repo.BubbleSeekBar;
import com.yrobot.exo.R;
import com.yrobot.exo.app.ConnectedPeripheralFragment;
import com.yrobot.exo.app.utils.Param;
import com.yrobot.exo.app.utils.ParamManager;
import com.yrobot.exo.app.YrConstants;

import static com.yrobot.exo.app.YrConstants.configBubbleSeekBar;
import static com.yrobot.exo.app.utils.Param.PARAM_TYPE_BOOL;
import static com.yrobot.exo.app.utils.Param.PARAM_TYPE_FLOAT;
import static com.yrobot.exo.app.utils.Param.PARAM_TYPE_INT16;
import static com.yrobot.exo.app.utils.Param.PARAM_TYPE_INT8;
import static com.yrobot.exo.app.utils.Param.PARAM_TYPE_SIGNAL;
import static com.yrobot.exo.app.YrConstants.KEY_FEEDBACK_PACKET_STATUS;
import static com.yrobot.exo.app.YrConstants.KEY_PARAM_REQUEST;
import static com.yrobot.exo.app.YrConstants.KEY_PARAM_SET;

public class ParamFragment extends ConnectedPeripheralFragment {
    // Log
    private final static String TAG = "yr-" + ParamFragment.class.getSimpleName();

    private Button btnLoadParams;
    private View mView;
    private boolean mLoaded = false;
    private int maxTextViewWidth = 0;

    public static ParamFragment newInstance(@Nullable String singlePeripheralIdentifier) {
        ParamFragment fragment = new ParamFragment();
        fragment.setArguments(createFragmentArgs(singlePeripheralIdentifier));
        return fragment;
    }

    public ParamFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_param, container, false);

        mView = view;

        btnLoadParams = view.findViewById(R.id.btn_load_param);
        if (mLoaded) {
            btnLoadParams.setVisibility(View.GONE);
        } else {
            btnLoadParams.setOnClickListener(v -> {
                sendPacketSelect(KEY_PARAM_REQUEST);
            });
        }

        loadParamView();

        return view;
    }

    @Override
    public void onRxFirstMessage() {
        Log.v(TAG, "onRxFirstMessage [" + rx_count + "]");
        sendPacketSelect(KEY_FEEDBACK_PACKET_STATUS);
    }

    @Override
    public void onRxData() {
        loadParamView();
    }

    private void addView(final Param param) {
        if (mView == null) {
            return;
        }

        new Handler(Looper.getMainLooper()).post(() -> {
            LinearLayout layout = mView.findViewById(R.id.param_ll);

            TextView textViewLabel = new TextView(getContext());
            textViewLabel.setText(param.name);
            textViewLabel.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            int textViewWidth = textViewLabel.getMeasuredWidth();
            if (textViewWidth > maxTextViewWidth) {
                maxTextViewWidth = textViewWidth;
            }

            Typeface typeface = getResources().getFont(R.font.opensans);
            textViewLabel.setTypeface(typeface);

            if (param.type >= PARAM_TYPE_INT16 && param.type <= PARAM_TYPE_FLOAT) {

                layout.addView(textViewLabel);

                BubbleSeekBar seekBar = new BubbleSeekBar(getContext());

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                int margin = YrConstants.dpToPx(getContext(), 10.0f);
                int margin_side = YrConstants.dpToPx(getContext(), 10.0f);
                lp.setMargins(margin_side, margin, 0, margin);
                seekBar.setLayoutParams(lp);

                float progress = param.getVal();

                seekBar.setProgress(progress);

                seekBar.setOnProgressChangedListener(new BubbleSeekBar.OnProgressChangedListener() {
                    @Override
                    public void onProgressChanged(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat, boolean fromUser) {
                        param.setVal(progressFloat);
                        final float factor = progressFloat;
                        byte[] bytes = param.getBytes(factor);
                        bytes = YrConstants.addKey(bytes, (byte) param.key);
                        sendByteBuffer(KEY_PARAM_SET, bytes);
                    }

                    @Override
                    public void getProgressOnActionUp(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat) {
                    }

                    @Override
                    public void getProgressOnFinally(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat, boolean fromUser) {
                    }
                });

                configBubbleSeekBar(getContext(), seekBar, param.getMin(), param.getMax(), progress);

                layout.addView(seekBar);

            } else if (param.type == PARAM_TYPE_SIGNAL) {

                Button button = new Button(getContext());

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                int margin = YrConstants.dpToPx(getContext(), 15.0f);
                int margin_side = YrConstants.dpToPx(getContext(), 40.0f);
                lp.setMargins(margin_side, 0, margin_side, margin);
                button.setLayoutParams(lp);

                button.setText(param.name);

                button.setTypeface(typeface);

                button.setOnClickListener(v -> {
                    byte[] bytes = YrConstants.addKey(new byte[]{1}, (byte) param.key);
                    sendByteBuffer(KEY_PARAM_SET, bytes);
                });

                layout.addView(button);

            } else if (param.type == PARAM_TYPE_INT8 || param.type == PARAM_TYPE_BOOL) {

                SwitchCompat boolSwitch = new SwitchCompat(getContext());

                boolSwitch.setChecked(param.getVal() > 0);

                boolSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (buttonView.isPressed()) {
                        byte data = (byte) (isChecked ? 1 : 0);
                        param.setVal(data);
                        byte[] bytes = param.getBytes(data);
                        bytes = YrConstants.addKey(bytes, (byte) param.key);
                        sendByteBuffer(KEY_PARAM_SET, bytes);
                    }
                });

                LinearLayout lin_layout = new LinearLayout(getContext());
                int width = YrConstants.dpToPx(getContext(), 180.0f);
                Log.v(TAG, "PARAM Width [" + textViewLabel.getMeasuredWidth() + "] [" + maxTextViewWidth + "] [" + width + "]");
                if (maxTextViewWidth > width) {
                    width = maxTextViewWidth + YrConstants.dpToPx(getContext(), 100.0f);
                }
//                lin_layout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                lin_layout.setLayoutParams(new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));
                lin_layout.setOrientation(LinearLayout.HORIZONTAL);

                int pad = YrConstants.dpToPx(getContext(), 8.0f);
                lin_layout.setPadding(0, pad, 0, pad);

                textViewLabel.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                ((LinearLayout.LayoutParams) textViewLabel.getLayoutParams()).weight = 1;
                ((LinearLayout.LayoutParams) textViewLabel.getLayoutParams()).gravity = Gravity.LEFT;
//
                boolSwitch.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
//                int pad_left = YrConstants.dpToPx(getContext(), 10.0f);
//                boolSwitch.setPadding(pad_left, 0, 0, 0);
                boolSwitch.setScaleX(1.3f);
                boolSwitch.setScaleY(1.1f);
                int pad_right = YrConstants.dpToPx(getContext(), 15.0f);
                boolSwitch.setPadding(0, 0, pad_right, 0);
                ((LinearLayout.LayoutParams) boolSwitch.getLayoutParams()).weight = 1;
                ((LinearLayout.LayoutParams) boolSwitch.getLayoutParams()).gravity = Gravity.RIGHT;

                lin_layout.addView(textViewLabel);
                lin_layout.addView(boolSwitch);

                layout.addView(lin_layout);
            }
        });
    }

    private void loadParamView() {
        if (!mLoaded) {
            if (ParamManager.getInstance().isInitialized()) {
                for (Param param : ParamManager.getInstance().getParams()) {
                    addView(param);
                }
                new Handler(Looper.getMainLooper()).post(() -> {
                    btnLoadParams.setVisibility(View.GONE);
                });
                mLoaded = true;
            }
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Update ActionBar
        setActionBarTitle(R.string.info_tab_title);

//        ExoData.getInstance().motorDataL.calibrated.setOnStateChange(() -> {
//            Log.v(TAG, "Calibration changed [" + ExoData.getInstance().motorDataL.calibrated.state + "]");
//            if (ExoData.getInstance().motorDataL.calibrated.state) {
////                cancelTimeout();
//            }
//        });
//        onRxData();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_help, menu);
    }
}
