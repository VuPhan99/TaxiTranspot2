package com.dacs.sict.htxv.taxitranspot.View;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetDialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.dacs.sict.htxv.taxitranspot.R;

public class BottomSheetRiderFragment extends BottomSheetDialogFragment {
    String mLocation, mDestination;
    String mTag;
    public static BottomSheetRiderFragment newInstance(String location, String destination)
    {
        BottomSheetRiderFragment f = new BottomSheetRiderFragment();
        Bundle args = new Bundle();
        args.putString("Location",location);
        args.putString( "Destination", destination );
        f.setArguments(args);
        return  f;

    }
    //ctr+o

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLocation = getArguments().getString( "location" );
        mDestination = getArguments().getString( "destination" );

    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_rider,container,false);
        return view;
    }
}
