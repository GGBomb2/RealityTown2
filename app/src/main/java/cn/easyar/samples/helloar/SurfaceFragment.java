package cn.easyar.samples.helloar;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.hardware.Camera;
import android.graphics.PixelFormat;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.search.core.PoiInfo;
import com.baidu.mapapi.search.core.RouteLine;
import com.baidu.mapapi.search.route.WalkingRouteLine;
import com.baidu.mapapi.utils.DistanceUtil;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Created by GGBomb2 on 2015/11/6.
 */
public class SurfaceFragment extends Fragment implements SurfaceHolder.Callback {
    // UI???
    boolean isFirstLoc = true;// ??????????
    private Camera myCamera = null;  //?????
    private boolean isPreview = false;
    private static final String tag = "yan";
    private Camera.AutoFocusCallback myAutoFocusCallback = null;//??????
    private SurfaceView mPreviewSV = null; //???SurfaceView
    private SurfaceHolder mySurfaceHolder = null;
    private GuideFragment mGuideFragment = null;
    public boolean isGuiding = false;//??????????
    RouteLine mroute = null;//????
    Object Nextstep = null;//??????????
    PoiInfo[] poiinfos = null;

    //?????
    private static final int EXIT_TIME = 2000;// ??????????????????
    private long firstExitTime = 0L;// ???????????????????????
    public String mData;
    public String address;
    private final float MAX_ROATE_DEGREE = 1.0f;// ????????????360??
    private SensorManager mSensorManager;// ?????????????
    private Sensor mOrientationSensor;// ??????????
    private AccelerateInterpolator mInterpolator;// ??????????????????????????????????,???????????????
    CompassView mPointer;// ?????view
    private float mDirection;// ?????????
    private float mTargetDirection;// ???????
    private float mLRDirection;//?????????
    private float mUDDirection;//?????????
    private boolean mChinease;// ???????????????
    protected final Handler mHandler = new Handler();
    LinearLayout mDirectionLayout;// ??????????????????view
    LinearLayout mAngleLayout;// ????????????view
    View mViewGuide;
    //TextView mLatitudeTV;// ????
    //TextView mLongitudeTV;// ????
    View view = null;
    View mCompassView;
    ImageView mGuideAnimation;
    private boolean mStopDrawing;// ???????????????????

    //????????
    CompassView rPointer;// ????????view
    private float rDirection;// ?????????
    private float rTargetDirection;// ???????
    protected final Handler rHandler = new Handler();
    protected final Handler mOverlayHandler = new Handler();//?????????
    private boolean rStopDrawing;// ??????????????????????


    // ????????????????????????handler??????????20??????????????????????????????
    protected Runnable rCompassViewUpdater = new Runnable() {
        @Override
        public void run() {
            if (rPointer != null && !rStopDrawing) {
                if (mroute.getAllStep() == null) {
                    Toast.makeText(getActivity(), "getAllstep!=null", Toast.LENGTH_SHORT);
                }
                if (mroute != null || mroute.getAllStep() != null) {
                    Nextstep = mroute.getAllStep().get(1);
                }
                if (rDirection != rTargetDirection) {

                    // calculate the short routine
                    float to = rTargetDirection;
                    if (to - rDirection > 180) {
                        to -= 360;
                    } else if (to - rDirection < -180) {
                        to += 360;
                    }
                    // limit the max speed to MAX_ROTATE_DEGREE
                    float distance = to - rDirection;
                    if (Math.abs(distance) > MAX_ROATE_DEGREE) {
                        distance = distance > 0 ? MAX_ROATE_DEGREE
                                : (-1.0f * MAX_ROATE_DEGREE);
                    }

                    // need to slow down if the distance is short
                    rDirection = normalizeDegree(rDirection
                            + ((to - rDirection) * mInterpolator
                            .getInterpolation(Math.abs(distance) > MAX_ROATE_DEGREE ? 0.4f
                                    : 0.3f)));// ???????????????????????????
                    rPointer.updateDirection(rDirection);// ????????????
                }

                //updateDirection();// ?????????

                rHandler.postDelayed(rCompassViewUpdater, 20);// 20?????????????????????????
            }
        }
    };


    protected Handler invisiableHandler = new Handler() {
        public void handleMessage(Message msg) {
            mViewGuide.setVisibility(View.GONE);
        }
    };
    // ??????????????????????handler??????????20????????????????????????????
    protected Runnable mCompassViewUpdater = new Runnable() {
        @Override
        public void run() {
            if (mPointer != null && !mStopDrawing) {
                if (mDirection != mTargetDirection) {

                    // calculate the short routine
                    float to = mTargetDirection;
                    if (to - mDirection > 180) {
                        to -= 360;
                    } else if (to - mDirection < -180) {
                        to += 360;
                    }

                    // limit the max speed to MAX_ROTATE_DEGREE
                    float distance = to - mDirection;
                    if (Math.abs(distance) > MAX_ROATE_DEGREE) {
                        distance = distance > 0 ? MAX_ROATE_DEGREE
                                : (-1.0f * MAX_ROATE_DEGREE);
                    }

                    // need to slow down if the distance is short
                    mDirection = normalizeDegree(mDirection
                            + ((to - mDirection) * mInterpolator
                            .getInterpolation(Math.abs(distance) > MAX_ROATE_DEGREE ? 0.4f
                                    : 0.3f)));// ???????????????????????????
                    mPointer.updateDirection(mDirection);// ????????????
                }

                updateDirection();// ?????????

                mHandler.postDelayed(mCompassViewUpdater, 20);// 20?????????????????????????
            }
        }
    };

    // Overlay????handler??????????20????????????????????????????
    protected Runnable mOverlayUpdater = new Runnable() {
        @Override
        public void run() {
            RelativeLayout raly2 = (RelativeLayout) view.findViewById(R.id.relativelayout_overlay);
            raly2.removeAllViews();
            TextView tx = (TextView) view.findViewById(R.id.textView);
            tx.setText("");
            if (poiinfos != null && ((MainActivity) getActivity()).mBaiduMap != null) {
                int count = poiinfos.length;

                LatLng mLocation = new LatLng(((MainActivity) getActivity()).mBaiduMap.getLocationData().latitude, ((MainActivity) getActivity()).mBaiduMap.getLocationData().longitude);
                for (int n = 0; n < count; n++) {                                                                   //?????????????
                    ImageView imageView = new ImageView(getActivity());
                    imageView.setImageResource(R.drawable.favicon);
                    double distance = DistanceUtil.getDistance(mLocation, poiinfos[n].location);                //??????
                    if (distance > 1000) {                                                                       //???????1000??????
                        continue;
                    }
                    double rdirection = Math.atan(((poiinfos[n].location.latitude - mLocation.latitude)) / ((poiinfos[n].location.longitude - mLocation.longitude)));//????????????????
                    //normalizeDegree((((float)rdirection)-direction)*-1.0f);
                    rdirection = normalizeDegree((float) (180 * rdirection / Math.PI));
                    rdirection = normalizeDegree((float) (mTargetDirection - rdirection - 90.0f));
                    tx.append(Integer.toString(n) + "\t rdirection: " + Double.toString(rdirection) + "\nmTargetDirection: " + Float.toString(mTargetDirection) + "\n");
                    if (Math.abs(rdirection) > 90 && Math.abs(rdirection) < 270) {
                        continue;
                    }
                    int height = (int) (200 - ((double) 3 / 25) * distance);
                    int width = height / 3 * 5;
                    RelativeLayout.LayoutParams lp1 = new RelativeLayout.LayoutParams(width, height);
                    lp1.topMargin = (int) ((((float) 1 / 3) - Math.cos(mUDDirection * Math.PI / 180)) * raly2.getHeight() - 0.5f * height);                            //???????top
                    //if(lp1.topMargin<0) continue;
                    lp1.leftMargin = (int) ((((float) 1 / 2) + Math.sin(rdirection * Math.PI / 180)) * raly2.getWidth() - 0.5f * width);                           //???????left
                    //if(lp1.leftMargin<0) continue;
                    raly2.addView(imageView, lp1);
                }
            }
            mOverlayHandler.postDelayed(mOverlayUpdater, 20);
        }
    };

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.surface_fragment, container, false);
        //?????SurfaceView
        mPreviewSV = (SurfaceView) view.findViewById(R.id.previewSV);
        mySurfaceHolder = mPreviewSV.getHolder();
        mySurfaceHolder.setFormat(PixelFormat.TRANSLUCENT);//translucent????? transparent???
        mySurfaceHolder.addCallback(this);
        mySurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        //aim???
        //RelativeLayout raly=(RelativeLayout)view.findViewById(R.id.aimView);
        //raly.bringToFront();
        //Compass
        RelativeLayout raly2 = (RelativeLayout) view.findViewById(R.id.relativelayout_overlay);
        raly2.bringToFront();
        //setContentView(R.layout.activity_main);
        initResources();// ?????view
        initServices();// ?????????????????????
        //
        mCompassView.bringToFront();
        //?????????
        myAutoFocusCallback = new Camera.AutoFocusCallback() {
            public void onAutoFocus(boolean success, Camera camera) {
                if (success) {
                    Log.i(tag, "myAutoFocusCallback:success...");
                } else {
                    Log.i(tag, "myAutoFocusCallback:?????...");
                }
            }
        };
        //??????
        ImageButton button_guide = (ImageButton) view.findViewById(R.id.button_guide);
        button_guide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                FragmentManager fm = getFragmentManager();
                // ????Fragment????
                FragmentTransaction transaction = fm.beginTransaction();
                if (mGuideFragment == null) {
                    mGuideFragment = new GuideFragment();
                }
                // ?????Fragment????????id_content????
                transaction.replace(R.id.id_content, mGuideFragment, "GuideFragment");
                ((MainActivity) getActivity()).mGuideFragment = mGuideFragment;
                transaction.addToBackStack(null);
                transaction.commit();
            }
        });
        return view;
    }

    /*??????????SurfaceHolder.Callback????????????*/
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
    // ??SurfaceView/?????????????????????????????????????
    {
        // TODO Auto-generated method stub
        Log.i(tag, "SurfaceHolder.Callback:surfaceChanged!");
        initCamera();
        myCamera.cancelAutoFocus();
    }


    public void surfaceCreated(SurfaceHolder holder)
    // SurfaceView?????/??????????????????R?????????????????????
    {
        // TODO Auto-generated method stub
        myCamera = Camera.open();
        try {
            myCamera.setPreviewDisplay(mySurfaceHolder);
            Log.i(tag, "SurfaceHolder.Callback: surfaceCreated!");
        } catch (IOException e) {
            // TODO Auto-generated catch block
            if (null != myCamera) {
                myCamera.release();
                myCamera = null;
            }
            e.printStackTrace();
        }


    }


    public void surfaceDestroyed(SurfaceHolder holder)
    //???????????
    {
        // TODO Auto-generated method stub
        Log.i(tag, "SurfaceHolder.Callback??Surface Destroyed");
        if (null != myCamera) {
            myCamera.setPreviewCallback(null); /*??????PreviewCallback?????????????????????
            ??????????????????*/
            myCamera.stopPreview();
            isPreview = false;
            myCamera.release();
            myCamera = null;
        }

    }

    //????????
    public void initCamera() {
        if (isPreview) {
            myCamera.stopPreview();
        }
        if (null != myCamera) {
            Camera.Parameters myParam = myCamera.getParameters();
            //          //???????????
            //          WindowManager wm = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
            //          Display display = wm.getDefaultDisplay();
            //          Log.i(tag, "???????"+display.getWidth()+" ??????:"+display.getHeight());

            myParam.setPictureFormat(PixelFormat.JPEG);//??????????????????

            //          //???camera????picturesize??previewsize
            //          List<Size> pictureSizes = myParam.getSupportedPictureSizes();
            //          List<Size> previewSizes = myParam.getSupportedPreviewSizes();
            //          for(int i=0; i<pictureSizes.size(); i++){
            //              Size size = pictureSizes.get(i);
            //              Log.i(tag, "initCamera:?????????pictureSizes: width = "+size.width+"height = "+size.height);
            //          }
            //          for(int i=0; i<previewSizes.size(); i++){
            //              Size size = previewSizes.get(i);
            //              Log.i(tag, "initCamera:?????????previewSizes: width = "+size.width+"height = "+size.height);
            //
            //          }


            //????????????????
            //WindowManager wm = this.getWindowManager();
            //SurfaceView sv=(SurfaceView)view.findViewById(R.id.previewSV);
            if (mPreviewSV != null) {
                int width = mPreviewSV.getWidth();
                int height = mPreviewSV.getHeight();
                Camera.Size size = getOptimalPreviewSize(myCamera.getParameters().getSupportedPreviewSizes(), height, width);
                myParam.setPreviewSize(size.width, size.height);
                //myParam.set("rotation", 90);
                myCamera.setDisplayOrientation(90);
                myParam.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                myCamera.setParameters(myParam);
                myCamera.startPreview();
                myCamera.autoFocus(myAutoFocusCallback);
                myCamera.cancelAutoFocus();
                isPreview = true;
            } else {
                Toast.makeText(this.getActivity(), "???????????", Toast.LENGTH_SHORT).show();
            }

        }
    }

    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASP_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;
        if (sizes == null) return null;
        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        int targetHeight = h;
        //Try to find an size match aspect ratio and size
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASP_TOLERANCE) continue;
            if (Math.abs(ratio - targetRatio) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        //Cannot find the one match the aspect ratio,ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    // ?????view
    private void initResources() {
        mViewGuide = view.findViewById(R.id.view_guide);
        mViewGuide.setVisibility(View.VISIBLE);
        invisiableHandler.sendMessageDelayed(new Message(), 3000);
        mGuideAnimation = (ImageView) view.findViewById(R.id.guide_animation);
        mDirection = 0.0f;// ????????????
        mTargetDirection = 0.0f;// ??????????
        rDirection = 0.0f;// ????????????
        rTargetDirection = 0.0f;// ??????????
        mInterpolator = new AccelerateInterpolator();// ????????????????
        mStopDrawing = true;
        rStopDrawing = true;
        mChinease = TextUtils.equals(Locale.getDefault().getLanguage(), "zh");// ?????????????????????????
        mCompassView = view.findViewById(R.id.view_compass);// ??????????LinearLayout????????ImageView??????TextView
        mPointer = (CompassView) view.findViewById(R.id.compass_pointer);// ???????????view
        rPointer = (CompassView) view.findViewById(R.id.compassbackdround);
        // mLocationTextView = (TextView)
        // findViewById(R.id.textview_location);// ????????????TextView
        mDirectionLayout = (LinearLayout) view.findViewById(R.id.layout_direction);// ????????????????????????????LinearLayout
        mAngleLayout = (LinearLayout) view.findViewById(R.id.layout_angle);// ???????????????????LinearLayout

        // mPointer.setImageResource(mChinease ? R.drawable.compass_cn
        // : R.drawable.compass);// ?????????????????????????????
    }

    // ?????????????????????
    private void initServices() {
        // sensor manager
        mSensorManager = (SensorManager) this.getActivity().getSystemService(Context.SENSOR_SERVICE);
        mOrientationSensor = mSensorManager.getSensorList(
                Sensor.TYPE_ORIENTATION).get(0);//
        // Log.i("way", mOrientationSensor.getName());

        // location manager
        // mLocationManager = (LocationManager)
        // getSystemService(Context.LOCATION_SERVICE);
        // Criteria criteria = new Criteria();// ??????????????????????LocationProvider
        // criteria.setAccuracy(Criteria.ACCURACY_FINE);// ??????
        // criteria.setAltitudeRequired(false);// ????????????
        // criteria.setBearingRequired(false);// ?????????????
        // criteria.setCostAllowed(true);// ??????????
        // criteria.setPowerRequirement(Criteria.POWER_LOW);// ???????
        // mLocationProvider = mLocationManager.getBestProvider(criteria,
        // true);// ???????????Provider

    }

    // ???????????????????
    private void updateDirection() {
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        // ?????layout????????view
        mDirectionLayout.removeAllViews();
        mAngleLayout.removeAllViews();

        // ?????????mTargetDirection???????????????????
        ImageView east = null;
        ImageView west = null;
        ImageView south = null;
        ImageView north = null;
        float direction = normalizeDegree(mTargetDirection * -1.0f);
        if (direction > 22.5f && direction < 157.5f) {
            // east
            east = new ImageView(view.getContext());
            east.setImageResource(mChinease ? R.drawable.e_cn : R.drawable.e);
            east.setLayoutParams(lp);
        } else if (direction > 202.5f && direction < 337.5f) {
            // west
            west = new ImageView(view.getContext());
            west.setImageResource(mChinease ? R.drawable.w_cn : R.drawable.w);
            west.setLayoutParams(lp);
        }

        if (direction > 112.5f && direction < 247.5f) {
            // south
            south = new ImageView(view.getContext());
            south.setImageResource(mChinease ? R.drawable.s_cn : R.drawable.s);
            south.setLayoutParams(lp);
        } else if (direction < 67.5 || direction > 292.5f) {
            // north
            north = new ImageView(view.getContext());
            north.setImageResource(mChinease ? R.drawable.n_cn : R.drawable.n);
            north.setLayoutParams(lp);
        }
        // ?????????????????????????????????????
        if (mChinease) {
            // east/west should be before north/south
            if (east != null) {
                mDirectionLayout.addView(east);
            }
            if (west != null) {
                mDirectionLayout.addView(west);
            }
            if (south != null) {
                mDirectionLayout.addView(south);
            }
            if (north != null) {
                mDirectionLayout.addView(north);
            }
        } else {
            // north/south should be before east/west
            if (south != null) {
                mDirectionLayout.addView(south);
            }
            if (north != null) {
                mDirectionLayout.addView(north);
            }
            if (east != null) {
                mDirectionLayout.addView(east);
            }
            if (west != null) {
                mDirectionLayout.addView(west);
            }
        }
        // ????????????????????????????
        int direction2 = (int) direction;
        boolean show = false;
        if (direction2 >= 100) {
            mAngleLayout.addView(getNumberImage(direction2 / 100));
            direction2 %= 100;
            show = true;
        }
        if (direction2 >= 10 || show) {
            mAngleLayout.addView(getNumberImage(direction2 / 10));
            direction2 %= 10;
        }
        mAngleLayout.addView(getNumberImage(direction2));
        // ??????????????????
        ImageView degreeImageView = new ImageView(view.getContext());
        degreeImageView.setImageResource(R.drawable.degree);
        degreeImageView.setLayoutParams(lp);
        mAngleLayout.addView(degreeImageView);
    }

    // ???????????????????????ImageView
    private ImageView getNumberImage(int number) {
        ImageView image = new ImageView(view.getContext());
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        switch (number) {
            case 0:
                image.setImageResource(R.drawable.number_0);
                break;
            case 1:
                image.setImageResource(R.drawable.number_1);
                break;
            case 2:
                image.setImageResource(R.drawable.number_2);
                break;
            case 3:
                image.setImageResource(R.drawable.number_3);
                break;
            case 4:
                image.setImageResource(R.drawable.number_4);
                break;
            case 5:
                image.setImageResource(R.drawable.number_5);
                break;
            case 6:
                image.setImageResource(R.drawable.number_6);
                break;
            case 7:
                image.setImageResource(R.drawable.number_7);
                break;
            case 8:
                image.setImageResource(R.drawable.number_8);
                break;
            case 9:
                image.setImageResource(R.drawable.number_9);
                break;
        }
        image.setLayoutParams(lp);
        return image;
    }


    /*
    // ???????????
    private void updateLocation(Location location) {
        if (location == null) {
            // mLocationTextView.setText(R.string.getting_location);
            return;
        } else {
            // StringBuilder sb = new StringBuilder();
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            String latitudeStr;
            String longitudeStr;
            if (latitude >= 0.0f) {
                latitudeStr = getString(R.string.location_north,
                        getLocationString(latitude));
            } else {
                latitudeStr = getString(R.string.location_south,
                        getLocationString(-1.0 * latitude));
            }

            // sb.append("    ");

            if (longitude >= 0.0f) {
                longitudeStr = getString(R.string.location_east,
                        getLocationString(longitude));
            } else {
                longitudeStr = getString(R.string.location_west,
                        getLocationString(-1.0 * longitude));
            }
            mLatitudeTV.setText(latitudeStr);
            mLongitudeTV.setText(longitudeStr);
            // mLocationTextView.setText(sb.toString());//
            // ?????????????????????????????????????
        }
    }

    // ???????????????????
    private String getLocationString(double input) {
        int du = (int) input;
        int fen = (((int) ((input - du) * 3600))) / 60;
        int miao = (((int) ((input - du) * 3600))) % 60;
        return String.valueOf(du) + "??" + String.valueOf(fen) + "??"
                + String.valueOf(miao) + "??";
    }
*/
    // ??????????????
    private SensorEventListener mOrientationSensorEventListener = new SensorEventListener() {

        @Override
        public void onSensorChanged(SensorEvent event) {
            float direction = event.values[mSensorManager.DATA_X] * -1.0f;
            if (Nextstep != null) {
                LatLng nodeLocation = null;
                LatLng mLocation = null;
                if (Nextstep instanceof WalkingRouteLine.WalkingStep) {
                    nodeLocation = ((WalkingRouteLine.WalkingStep) Nextstep).getEntrance().getLocation();
                    if (((MainActivity) getActivity()).mBaiduMap != null) {
                        mLocation = new LatLng(((MainActivity) getActivity()).mBaiduMap.getLocationData().latitude, ((MainActivity) getActivity()).mBaiduMap.getLocationData().longitude);
                    }
                    if (nodeLocation != null && mLocation != null) {
                        double rdirection = Math.atan((nodeLocation.latitude - mLocation.latitude) / (nodeLocation.longitude - mLocation.longitude));
                        double exrTarget = (180 * rdirection / Math.PI) - 270.0f;
                        rTargetDirection = normalizeDegree((((float) exrTarget) - direction) * -1.0f);
                        //double x=((WalkingRouteLine.WalkingStep) Nextstep).getDirection();
                        //rTargetDirection=normalizeDegree((((WalkingRouteLine.WalkingStep) Nextstep).getDirection()-direction)*-1.0f);
                    }
                }
            }
            mUDDirection = normalizeDegree(event.values[1]);//????
            mLRDirection = normalizeDegree(event.values[2]);//?????????
            mTargetDirection = normalizeDegree(direction);// ???????????????????????
            // Log.i("way", event.values[mSensorManager.DATA_Y] + "");
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    // ?????????????????
    private float normalizeDegree(float degree) {
        return (degree + 720) % 360;
    }

    @Override
    public void onPause() {// ??????????????????????????????????????????

        super.onPause();
        mOverlayHandler.removeCallbacks(mOverlayUpdater);
        if (mOrientationSensor != null) {
            mSensorManager.unregisterListener(mOrientationSensorEventListener);
        }
    }

    @Override
    public void onResume() {// ??????????????????????????????????????????????

        super.onResume();
        // activity ???????????????
        if (mOrientationSensor != null) {
            mSensorManager.registerListener(mOrientationSensorEventListener,
                    mOrientationSensor, SensorManager.SENSOR_DELAY_GAME);
        } else {
            // Toast.makeText(this, R.string.cannot_get_sensor,
            // Toast.LENGTH_SHORT)
            // .show();
        }
        mStopDrawing = false;
        if (isGuiding) {
            rStopDrawing = false;
        }
        rHandler.postDelayed(rCompassViewUpdater, 20);
        mHandler.postDelayed(mCompassViewUpdater, 20);
        mOverlayHandler.postDelayed(mOverlayUpdater, 20);
    }

}
