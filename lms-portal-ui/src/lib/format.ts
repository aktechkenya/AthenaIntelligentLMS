export const formatKES = (amount: number) =>
  `KES ${amount.toLocaleString("en-KE", { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
export const formatKESFull = (amount: number) =>
  `KES ${amount.toLocaleString("en-KE", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
