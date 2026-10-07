package com.xyron.game.launcher.adapters;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.xyron.game.R;
import com.xyron.game.launcher.util.ButtonAnimator;

import org.w3c.dom.Text;

import java.util.ArrayList;

public class FaqAdapter extends RecyclerView.Adapter<FaqAdapter.ViewHolder> {

    private Activity mActivity;

    private ArrayList<String> mItemsMain = new ArrayList<>();
    private ArrayList<String> mItemsInfo = new ArrayList<>();

    private AlertDialog.Builder builder;

    public FaqAdapter(Activity activity)
    {
        mActivity = activity;
        mItemsMain.add("Server tidak merespons. Coba lagi");
        mItemsMain.add("Anda diblokir dari server ini");
        mItemsMain.add("Server mengirim baris berwarna biru");
        mItemsMain.add("Saya belum terdaftar di halaman login");
        mItemsMain.add("Saya tidak setuju dengan keputusan administrator");
        mItemsInfo.add("Coba mulai ulang launcher, lalu masuk kembali. Jika masalah berlanjut, hubungi kami melalui bagian dukungan teknis.");
        mItemsInfo.add("Masuk kembali ke permainan; jika tidak membantu, matikan Wi-Fi dan coba masuk menggunakan internet seluler. Jika kedua cara tidak berhasil, hubungi kami di Discord.");
        mItemsInfo.add("Nama panggilan Anda tidak memenuhi persyaratan SA-MP. Nama panggilan harus terdiri dari 6 hingga 21 karakter dan menyertakan karakter _.");
        mItemsInfo.add("Nama panggilan tersebut sudah digunakan. Ubah di launcher, lalu coba lagi.");
        mItemsInfo.add("Kirimkan keluhan kepada kami di Discord. Kami akan meninjaunya dalam 48 jam.");

        builder = new AlertDialog.Builder(mActivity);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.faq_item, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if(mItemsMain.size() > position)
        {
            holder.mFaqText.setText(mItemsMain.get(position));

            holder.mMain.setOnTouchListener(new ButtonAnimator(mActivity, holder.mMain));
            holder.mMain.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    builder.setMessage(mItemsInfo.get(holder.getAdapterPosition()))
                            .setCancelable(false)
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.dismiss();
                                }
                            })
                            .setNegativeButton("", null);
                    //Creating dialog box
                    AlertDialog alert = builder.create();
                    //Setting the title manually
                    alert.setTitle(mItemsMain.get(holder.getAdapterPosition()));
                    alert.show();
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return mItemsMain.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder
    {
        private View mView;
        public ImageView mMain;
        public TextView mFaqText;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mMain = view.findViewById(R.id.faq_main);
            mFaqText = view.findViewById(R.id.faq_text);
        }

        public View getView() {
            return mView;
        }
    }
}
