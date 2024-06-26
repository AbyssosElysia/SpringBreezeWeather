package com.elysia.springbreezeweather;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.elysia.springbreezeweather.db.City;
import com.elysia.springbreezeweather.db.County;
import com.elysia.springbreezeweather.db.Province;
import com.elysia.springbreezeweather.util.HttpUtil;
import com.elysia.springbreezeweather.util.Utility;

import org.litepal.LitePal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class ChooseAreaFragment extends Fragment {
    public static final int LEVEL_PROVINCE = 0;
    public static final int LEVEL_CITY = 1;
    public static final int LEVEL_COUNTY = 2;

    private ProgressDialog progressDialog;
    private TextView titleText;
    private Button backButton;
    private Button locationButton;

    private ListView listView;
    private ArrayAdapter<String> adapter;
    private List<String> dataList = new ArrayList<>();

    private List<Province> provinceList;
    private List<City> cityList;
    private List<County> countyList;

    private Province selectedProvince;
    private City selectedCity;

    private int currentLevel;

    private String wanted;

    private String district;

    private String latitude;
    private String  longitude;

    AMapLocationClientOption option;

    CountDownLatch latchLoc = new CountDownLatch(1);


    //声明AMapLocationClient类对象
    public AMapLocationClient mLocationClient = null;
    //声明定位回调监听器
    public AMapLocationListener mLocationListener = new AMapLocationListener() {
        @Override
        public void onLocationChanged(AMapLocation aMapLocation) {
            if (aMapLocation != null) {
                if (aMapLocation.getErrorCode() == 0) {
                    latitude = String.format("%.2f", aMapLocation.getLatitude());
                    longitude = String.format("%.2f", aMapLocation.getLongitude());

                    Toast.makeText(getContext(), "定位成功！！！", Toast.LENGTH_LONG).show();
                    latchLoc.countDown();
                } else {
                    // 显示错误信息ErrCode是错误码，errInfo是错误信息，详见错误码表。
                    String error = "定位失败: " + aMapLocation.getErrorCode() + ", " + aMapLocation.getErrorInfo();
                    Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
                    latchLoc.countDown();
                }
            }
        }
    };


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // LitePal.deleteDatabase("demo");

        View view = inflater.inflate(R.layout.choose_area, container, false);

        locationButton = (Button) view.findViewById(R.id.location_button);


        titleText = (TextView) view.findViewById(R.id.title_text);
        backButton = (Button) view.findViewById(R.id.back_button);
        locationButton = (Button) view.findViewById(R.id.location_button);
        listView = (ListView) view.findViewById(R.id.list_view);
        adapter = new ArrayAdapter<>(getContext(), R.layout.list_item_text, dataList);
        listView.setAdapter(adapter);
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {

        super.onActivityCreated(savedInstanceState);

        AMapLocationClient.updatePrivacyShow(getContext(), true, true);
        AMapLocationClient.updatePrivacyAgree(getContext(), true);
        try {
            mLocationClient = new AMapLocationClient(getContext());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        mLocationClient.setLocationListener(mLocationListener);
        option = new AMapLocationClientOption();
        option.setLocationPurpose(AMapLocationClientOption.AMapLocationPurpose.SignIn);
        option.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        option.setOnceLocation(true);
        mLocationClient.setLocationOption(option);
        option.setLocationCacheEnable(false);
        mLocationClient.stopLocation();
        mLocationClient.startLocation();


        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (currentLevel == LEVEL_PROVINCE) {
                    selectedProvince = provinceList.get(position);
                    queryCities();
                } else if (currentLevel == LEVEL_CITY) {
                    selectedCity = cityList.get(position);
                    queryCounties();
                } else if (currentLevel == LEVEL_COUNTY) {
                    showProgressDialog();
                    String url = "https://geoapi.qweather.com/v2/city/lookup?location="
                            + countyList.get(position).getAdcode()
                            + "&key=a500f59504f440e1af60bcdbc1c0a780";
                    CountDownLatch latch = new CountDownLatch(1);
                    HttpUtil.sendOkHttpRequest(url, new Callback() {
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getContext(), "加载失败", Toast.LENGTH_SHORT).show();
                                    closeProgressDialog();
                                    latch.countDown();
                                }
                            });

                        }

                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                            String responseText = response.body().string();
                            wanted = Utility.handleLocationResponse(responseText);
                            latch.countDown();
                        }
                    });
                    try {
                        latch.await();
                        if (getActivity() instanceof MainActivity) {
                            Intent intent = new Intent(getActivity(), WeatherActivity.class);
                            intent.putExtra("weather_id", wanted);
                            intent.putExtra("county_name", countyList.get(position).getCountyName());
                            // Toast.makeText(getContext(), Utility.handleLocationResponse(responseText), Toast.LENGTH_LONG).show();
                            startActivity(intent);
                            closeProgressDialog();
                            getActivity().finish();
                        } else if (getActivity() instanceof WeatherActivity) {
                            closeProgressDialog();
                            WeatherActivity weatherActivity = (WeatherActivity) getActivity();
                            weatherActivity.setCountyName(countyList.get(position).getCountyName());
                            weatherActivity.drawerLayout.closeDrawers();
                            weatherActivity.swipeRefreshLayout.setRefreshing(true);
                            weatherActivity.requestWeather(wanted);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }
            }
        });

        locationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mLocationClient.startLocation();

                try {
                    latchLoc.await();

                    String url = "https://geoapi.qweather.com/v2/city/lookup?location="
                            + longitude + "," + latitude
                            + "&key=a500f59504f440e1af60bcdbc1c0a780";
                    showProgressDialog();
                    CountDownLatch latch = new CountDownLatch(1);

                    HttpUtil.sendOkHttpRequest(url, new Callback() {
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getContext(), "加载失败", Toast.LENGTH_SHORT).show();
                                    closeProgressDialog();
                                    latch.countDown();
                                }
                            });

                        }

                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                            String responseText = response.body().string();
                            wanted = Utility.handleLocationResponse(responseText);
                            district = Utility.handleCityResponse(responseText);
                            latch.countDown();;
                            closeProgressDialog();
                        }
                    });
                    try {
                        latch.await();
                        if (getActivity() instanceof MainActivity) {
                            Intent intent = new Intent(getActivity(), WeatherActivity.class);
                            intent.putExtra("weather_id", wanted);
                            intent.putExtra("county_name", district);
                            // Toast.makeText(getContext(), Utility.handleLocationResponse(responseText), Toast.LENGTH_LONG).show();
                            startActivity(intent);
                            getActivity().finish();
                        } else if (getActivity() instanceof WeatherActivity) {
                            closeProgressDialog();
                            WeatherActivity weatherActivity = (WeatherActivity) getActivity();
                            weatherActivity.setCountyName(district);
                            weatherActivity.drawerLayout.closeDrawers();
                            weatherActivity.swipeRefreshLayout.setRefreshing(true);
                            weatherActivity.requestWeather(wanted);
                        }

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        });

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentLevel == LEVEL_COUNTY) {
                    queryCities();
                } else if (currentLevel == LEVEL_CITY) {
                    queryProvinces();
                }
            }
        });
        queryProvinces();
    }

    private void queryProvinces() {
        titleText.setText("中国");
        backButton.setVisibility(View.GONE);
        provinceList = LitePal.findAll(Province.class);
        if (!provinceList.isEmpty()) {
            dataList.clear();
            for (Province province : provinceList) {
                dataList.add(province.getProvinceName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_PROVINCE;
        } else {
            String address = "https://restapi.amap.com/v3/config/district?keywords=中国&key=7967ddda1494e4cfa31f468fb779dadc";
            queryFromServer(address, "province");
        }
    }

    private void queryCities() {
        titleText.setText(selectedProvince.getProvinceName());
        backButton.setVisibility(View.VISIBLE);
        cityList = LitePal.where("provinceAdcode = ?", String.valueOf(selectedProvince.getAdcode())).find(City.class);
        if (!cityList.isEmpty()) {
            dataList.clear();
            for (City city : cityList) {
                dataList.add(city.getCityName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_CITY;
        } else {
            String provinceName = selectedProvince.getProvinceName();
            String address = "https://restapi.amap.com/v3/config/district?keywords=" + provinceName + "&key=7967ddda1494e4cfa31f468fb779dadc";
            queryFromServer(address, "city");
        }
    }

    private void queryCounties() {
        titleText.setText(selectedCity.getCityName());
        backButton.setVisibility(View.VISIBLE);
        countyList = LitePal.where("cityAdcode = ?", String.valueOf(selectedCity.getAdcode())).find(County.class);
        if (!countyList.isEmpty()) {
            dataList.clear();
            for (County county : countyList) {
                dataList.add(county.getCountyName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_COUNTY;
        } else {
            String cityName = selectedCity.getCityName();
            String address = "https://restapi.amap.com/v3/config/district?keywords=" + cityName + "&key=7967ddda1494e4cfa31f468fb779dadc";
            queryFromServer(address, "county");
        }
    }

    private void queryFromServer(String address, final String type) {
        showProgressDialog();
        HttpUtil.sendOkHttpRequest(address, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closeProgressDialog();
                        Toast.makeText(getContext(), "加载失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseText = response.body().string();

                boolean result = false;
                if ("province".equals(type)) {
                    result = Utility.handleProvinceResponse(responseText);
                } else if ("city".equals(type)) {
                    result = Utility.handleCityResponse(responseText, selectedProvince.getAdcode());
                } else if ("county".equals(type)) {
                    result = Utility.handleCountyResponse(responseText, selectedCity.getAdcode());
                }
                if (result) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            closeProgressDialog();
                            if ("province".equals(type)) {
                                queryProvinces();
                            } else if ("city".equals(type)) {
                                queryCities();
                            } else if ("county".equals(type)) {
                                queryCounties();
                            }
                        }
                    });
                }
            }
        });
    }

    private void showProgressDialog() {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(getActivity());
            progressDialog.setMessage("正在加载中！！！");
            progressDialog.setCanceledOnTouchOutside(false);
        }
        progressDialog.show();
    }

    private void closeProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }

}
